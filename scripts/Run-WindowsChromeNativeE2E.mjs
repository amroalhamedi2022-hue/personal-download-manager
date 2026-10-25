// Real installed Windows Chrome MV3 extension -> HyperGet nativeMessaging.
// Node 22 built-ins and Chrome DevTools Protocol; no new npm dependencies.
// This checks the browser API, not HTTP download capture or cookies.
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { setTimeout as sleep } from 'node:timers/promises';

const [chromeArg, extensionArg, evidenceArg] = process.argv.slice(2);
if (!chromeArg || !extensionArg || !evidenceArg) {
  throw Error('Usage: node Run-WindowsChromeNativeE2E.mjs CHROME_EXE EXTENSION_DIR EVIDENCE_DIR');
}
const chromeExe = resolve(chromeArg);
const extensionDir = resolve(extensionArg);
const evidenceDir = resolve(evidenceArg);
for (const path of [chromeExe, join(extensionDir, 'manifest.json')]) {
  if (!existsSync(path)) throw Error('Missing required file: ' + path);
}
const manifest = JSON.parse(readFileSync(join(extensionDir, 'manifest.json'), 'utf8'));
if (manifest.manifest_version !== 3 || !manifest.permissions?.includes('nativeMessaging') || !manifest.key) {
  throw Error('Chrome extension requires MV3, nativeMessaging permission and stable public key');
}
const hash = createHash('sha256').update(Buffer.from(manifest.key, 'base64')).digest();
const extensionId = [...hash.subarray(0, 16)].flatMap(b => [b >> 4, b & 15])
  .map(n => String.fromCharCode(97 + n)).join('');
const pagePath = manifest.options_ui?.page;
if (!pagePath || pagePath.includes('..') || pagePath.startsWith('/')) {
  throw Error('Extension options page missing or unsafe');
}
mkdirSync(evidenceDir, { recursive: true });
const profileDir = join(tmpdir(), 'hyperget-chrome-native-' + process.pid);
const hostName = 'app.personaldownloadmanager'; // retained compatibility identity
let chrome = null;
let browserSocket = null;

async function poll(fn, label, timeout = 45000) {
  const deadline = Date.now() + timeout;
  let last;
  while (Date.now() < deadline) {
    if (chrome && chrome.exitCode !== null) throw Error('Chrome exited before ' + label);
    try {
      const result = await fn();
      if (result) return result;
    } catch (e) { last = e; }
    await sleep(400);
  }
  throw Error('Timeout waiting for ' + label + ': ' + (last?.message ?? 'not ready'));
}
class Cdp {
  constructor(url) {
    this.ws = new WebSocket(url);
    this.nextId = 0;
    this.pending = new Map();
    this.ws.addEventListener('message', event => {
      const msg = JSON.parse(event.data);
      const waiter = this.pending.get(msg.id);
      if (!waiter) return;
      this.pending.delete(msg.id);
      clearTimeout(waiter.timer);
      if (msg.error) waiter.reject(Error('CDP error: ' + msg.error.message));
      else waiter.resolve(msg.result);
    });
    this.ws.addEventListener('close', () => this.rejectAll('CDP closed'));
    this.ws.addEventListener('error', () => this.rejectAll('CDP failed'));
  }
  rejectAll(reason) {
    for (const waiter of this.pending.values()) {
      clearTimeout(waiter.timer);
      waiter.reject(Error(reason));
    }
    this.pending.clear();
  }
  async ready() {
    if (this.ws.readyState === WebSocket.OPEN) return;
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(Error('CDP connect timeout')), 15000);
      this.ws.addEventListener('open', () => { clearTimeout(timer); resolve(); }, { once: true });
      this.ws.addEventListener('error', () => { clearTimeout(timer); reject(Error('CDP connect failed')); }, { once: true });
    });
  }
  call(method, params = {}) {
    const id = ++this.nextId;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(Error('CDP request timed out: ' + method));
      }, 30000);
      this.pending.set(id, { resolve, reject, timer });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }
  close() { this.ws.close(); }
}

try {
  chrome = spawn(chromeExe, [
    '--headless=new', '--no-first-run', '--no-default-browser-check',
    '--disable-background-networking', '--disable-gpu',
    '--remote-debugging-port=0', '--user-data-dir=' + profileDir,
    '--disable-extensions-except=' + extensionDir, '--load-extension=' + extensionDir,
    'about:blank',
  ], { stdio: 'ignore', windowsHide: true });
  chrome.on('error', () => {});
  const port = await poll(() => {
    const file = join(profileDir, 'DevToolsActivePort');
    if (!existsSync(file)) return null;
    const value = Number(readFileSync(file, 'utf8').split(/\r?\n/)[0]);
    return Number.isInteger(value) && value > 0 ? value : null;
  }, 'Chrome DevTools port');
  const version = await poll(async () => {
    const response = await fetch('http://127.0.0.1:' + port + '/json/version');
    return response.ok ? response.json() : null;
  }, 'Chrome debugger');
  browserSocket = new Cdp(version.webSocketDebuggerUrl);
  await browserSocket.ready();
  const url = 'chrome-extension://' + extensionId + '/' + pagePath;
  const target = await browserSocket.call('Target.createTarget', { url });
  if (!target?.targetId) throw Error('Could not create extension page');
  const tab = await poll(async () => {
    const response = await fetch('http://127.0.0.1:' + port + '/json/list');
    if (!response.ok) return null;
    return (await response.json()).find(x => x.id === target.targetId && x.webSocketDebuggerUrl);
  }, 'Chrome extension target');
  const page = new Cdp(tab.webSocketDebuggerUrl);
  await page.ready();
  try {
    await page.call('Runtime.enable');
    await poll(async () => {
      const state = await page.call('Runtime.evaluate', {
        expression: '({ id: globalThis.chrome?.runtime?.id ?? null, origin: location.origin, ready: document.readyState })',
        returnByValue: true,
      });
      const value = state.result?.value;
      return value?.id === extensionId && value?.origin === 'chrome-extension://' + extensionId
        && value?.ready === 'complete';
    }, 'loaded Chrome extension page');
    const results = [];
    for (const [id, action] of [
      ['B_chrome_native_1', 'ping'],
      ['B_chrome_native_2', 'ping'],
      ['B_chrome_native_unknown', 'hyperget-nonexistent-action'],
    ]) {
      const expression = '(async () => { ' +
        'if (!globalThis.chrome?.runtime?.sendNativeMessage) throw Error("nativeMessaging API unavailable");' +
        'if (chrome.runtime.id !== ' + JSON.stringify(extensionId) + ') throw Error("extension ID mismatch");' +
        'return await chrome.runtime.sendNativeMessage(' + JSON.stringify(hostName) + ', ' +
        JSON.stringify({ id, content: { action, isError: false, payload: 'null' } }) + '); })()';
      const evaluated = await page.call('Runtime.evaluate', {
        expression, awaitPromise: true, returnByValue: true,
      });
      if (evaluated.exceptionDetails) throw Error('Chrome native request failed: ' + action);
      const reply = evaluated.result?.value;
      if (reply?.id !== id) throw Error('Chrome request correlation failed: ' + action);
      if (action === 'ping') {
        if (reply.content?.isError !== false || reply.content?.payload !== 'true') {
          throw Error('Chrome native ping failed');
        }
      } else {
        if (reply.content?.isError !== true) throw Error('Unknown action was not rejected');
        const safe = JSON.parse(reply.content.payload);
        if (safe.errorType !== 'REQUEST_FAILED' ||
            safe.message !== 'HyperGet could not complete the browser request.') {
          throw Error('Browser error privacy policy failed');
        }
      }
      results.push({ action, correlated: true, result: action === 'ping' ? 'PASS' : 'SAFE_ERROR_PASS' });
    }
    writeFileSync(join(evidenceDir, 'chrome-native-extension-report.json'), JSON.stringify({
      browser: 'Google Chrome', extensionId, manifestVersion: manifest.manifest_version,
      nativeHost: hostName, realChromeExtensionLoaded: true, requests: results,
      browserDownloadCaptured: false, verified: true,
    }, null, 2));
    console.log('PASS: real Chrome extension -> installed HyperGet native host; two pings and safe error');
  } finally { page.close(); }
} finally {
  browserSocket?.close();
  if (chrome?.pid) {
    spawnSync('taskkill.exe', ['/PID', String(chrome.pid), '/T', '/F'],
      { stdio: 'ignore', windowsHide: true });
  }
}
