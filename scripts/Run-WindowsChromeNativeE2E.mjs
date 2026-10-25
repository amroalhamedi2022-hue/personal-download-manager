// Real installed Windows Chrome MV3 extension -> HyperGet nativeMessaging.
// Node 22 built-ins and Chrome DevTools Protocol; no new npm dependencies.
// This checks the browser API, not HTTP download capture or cookies.
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { homedir, tmpdir } from 'node:os';
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
    await page.call('Page.enable');
    // Chrome may create a tab before the unpacked extension is registered.
    // Retry navigation once after startup rather than testing the initial error page.
    let lastState = null;
    let navigatedAgain = false;
    const navigationStart = Date.now();
    try {
      await poll(async () => {
        const state = await page.call('Runtime.evaluate', {
          expression: '({ id: globalThis.chrome?.runtime?.id ?? null, origin: location.origin, ready: document.readyState })',
          returnByValue: true,
        });
        lastState = state.result?.value ?? null;
        if (lastState?.id === extensionId &&
            lastState?.origin === 'chrome-extension://' + extensionId &&
            lastState?.ready === 'complete') return true;
        if (!navigatedAgain && Date.now() - navigationStart > 4000) {
          navigatedAgain = true;
          await page.call('Page.navigate', { url });
        }
        return false;
      }, 'loaded Chrome extension page', 30000);
    } catch {
      // Only fixed-origin browser state; never print page contents, cookies or URLs.
      throw Error('Chrome extension context unavailable: ' + JSON.stringify(lastState));
    }
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
    // Acceptance HG-BROWSER-CHROME-03, recorded before the Windows installer run:
    // Does a real Chrome MV3 extension keep a connected native host alive for
    // concurrent requests, correlate every reply, disconnect, then reconnect?
    // Expect 3/3 replies on the first port and 1/1 after reconnect, in addition
    // to the existing 3 one-shot replies. Source mocks cannot exercise Chrome
    // nativeMessaging host lifetime, Windows registration or port disconnects.
    // The existing single installed-NSIS workflow is the purposeful runtime gate.
    async function exerciseConnectedPort(prefix, actions) {
      const expression = `(async () => {
        const actions = ${JSON.stringify(actions)};
        const prefix = ${JSON.stringify(prefix)};
        const host = ${JSON.stringify(hostName)};
        const port = chrome.runtime.connectNative(host);
        return await new Promise((resolve, reject) => {
          let settled = false;
          const pending = new Map(actions.map((action, index) =>
            [prefix + '_' + index, action]));
          const timer = setTimeout(() => fail('Native port response timed out'), 15000);
          function finish(value, error) {
            if (settled) return;
            settled = true;
            clearTimeout(timer);
            port.disconnect();
            if (error) reject(Error(error));
            else resolve(value);
          }
          function fail(message) { finish(null, message); }
          port.onDisconnect.addListener(() => {
            if (!settled) fail('Native port disconnected before all replies');
          });
          port.onMessage.addListener(reply => {
            if (settled) return;
            const action = pending.get(reply?.id);
            if (!action) { fail('Native port reply has unknown or duplicate ID'); return; }
            pending.delete(reply.id);
            if (action === 'ping') {
              if (reply.content?.isError !== false ||
                  reply.content?.payload !== 'true') {
                fail('Native port ping failed'); return;
              }
            } else {
              if (reply.content?.isError !== true) {
                fail('Unknown native action not rejected'); return;
              }
              let safe;
              try { safe = JSON.parse(reply.content.payload); }
              catch { fail('Native port error is not JSON'); return; }
              if (safe.errorType !== 'REQUEST_FAILED' ||
                  safe.message !== 'HyperGet could not complete the browser request.') {
                fail('Native port error privacy check failed'); return;
              }
            }
            if (pending.size === 0) finish({ replies: actions.length, correlated: true });
          });
          actions.forEach((action, index) =>
            port.postMessage({
              id: prefix + '_' + index,
              content: { action, isError: false, payload: 'null' },
            }));
        });
      })()`;
      const evaluated = await page.call('Runtime.evaluate', {
        expression, awaitPromise: true, returnByValue: true,
      });
      if (evaluated.exceptionDetails ||
          evaluated.result?.value?.replies !== actions.length ||
          evaluated.result?.value?.correlated !== true) {
        throw Error('Real Chrome connected native port acceptance failed');
      }
      return { replies: actions.length, correlated: true };
    }
    const firstPort = await exerciseConnectedPort('B_chrome_port_a',
      ['ping', 'ping', 'hyperget-nonexistent-action']);
    const reconnectedPort = await exerciseConnectedPort('B_chrome_port_b', ['ping']);

    // HG-BROWSER-CHROME-04: browser extension -> native host -> installed
    // GUI IPC -> real loopback HTTP GET -> on-disk SHA-256. The fixture is
    // local, deterministic and has no cookies, credentials or external URLs.
    const fixtureName = 'hyperget-browser-capture-' + process.pid + '.bin';
    const fixtureBytes = Buffer.alloc(2 * 1024 * 1024);
    for (let index = 0; index < fixtureBytes.length; index++) {
      fixtureBytes[index] = index % 251;
    }
    const expectedSha = createHash('sha256').update(fixtureBytes).digest('hex');
    let fixtureGetCount = 0;
    const fixtureServer = createServer((request, response) => {
      const pathname = new URL(request.url ?? '/', 'http://127.0.0.1').pathname;
      if (pathname !== '/' + fixtureName) {
        response.writeHead(404); response.end(); return;
      }
      if (request.method !== 'GET' && request.method !== 'HEAD') {
        response.writeHead(405); response.end(); return;
      }
      const range = request.headers.range;
      const match = range ? /^bytes=(\\d+)-(\\d*)$/.exec(range) : null;
      if (range && !match) {
        response.writeHead(416, { 'Content-Range': 'bytes */' + fixtureBytes.length });
        response.end(); return;
      }
      const start = match ? Number(match[1]) : 0;
      const end = match && match[2] ? Number(match[2]) : fixtureBytes.length - 1;
      if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) ||
          start < 0 || start >= fixtureBytes.length || end < start ||
          end >= fixtureBytes.length) {
        response.writeHead(416, { 'Content-Range': 'bytes */' + fixtureBytes.length });
        response.end(); return;
      }
      const headers = {
        'Content-Type': 'application/octet-stream',
        'Content-Length': String(end - start + 1),
        'Content-Disposition': 'attachment; filename="' + fixtureName + '"',
        'Accept-Ranges': 'bytes',
        'Cache-Control': 'no-store',
      };
      if (match) headers['Content-Range'] = 'bytes ' + start + '-' + end + '/' + fixtureBytes.length;
      response.writeHead(match ? 206 : 200, headers);
      if (request.method === 'GET') {
        fixtureGetCount++;
        response.end(fixtureBytes.subarray(start, end + 1));
      } else {
        response.end();
      }
    });
    await new Promise((resolve, reject) => {
      fixtureServer.once('error', reject);
      fixtureServer.listen(0, '127.0.0.1', resolve);
    });
    let captureVerified = false;
    try {
      const port = fixtureServer.address().port;
      const downloadUrl = 'http://127.0.0.1:' + port + '/' + fixtureName;
      const requestId = 'B_chrome_download_' + process.pid;
      const addRequest = {
        id: requestId,
        content: {
          action: 'add',
          isError: false,
          payload: JSON.stringify({
            items: [{
              link: downloadUrl, downloadPage: null, headers: null,
              description: null, suggestedName: fixtureName, type: 'http',
            }],
            options: { silentAdd: true, silentStart: true },
          }),
        },
      };
      const evaluated = await page.call('Runtime.evaluate', {
        expression: '(async () => await chrome.runtime.sendNativeMessage(' +
          JSON.stringify(hostName) + ', ' + JSON.stringify(addRequest) + '))()',
        awaitPromise: true, returnByValue: true,
      });
      if (evaluated.exceptionDetails) {
        throw Error('Chrome native add request failed');
      }
      const addReply = evaluated.result?.value;
      if (addReply?.id !== requestId || addReply.content?.isError !== false ||
          addReply.content?.payload !== 'true') {
        throw Error('Installed HyperGet rejected browser-origin add request');
      }

      // Only look for our unique fixture filename under standard local user
      // download directories. Never enumerate or report unrelated filenames.
      function findCapturedFile(directory, depth = 3) {
        if (!existsSync(directory)) return null;
        for (const entry of readdirSync(directory, { withFileTypes: true })) {
          const child = join(directory, entry.name);
          if (entry.isFile() && entry.name === fixtureName) return child;
          if (entry.isDirectory() && depth > 0 && !entry.name.startsWith('.')) {
            const found = findCapturedFile(child, depth - 1);
            if (found) return found;
          }
        }
        return null;
      }
      const roots = ['Downloads', 'Desktop', 'Documents'].map(name => join(homedir(), name));
      await poll(() => {
        for (const root of roots) {
          const path = findCapturedFile(root);
          if (!path || statSync(path).size !== fixtureBytes.length) continue;
          if (createHash('sha256').update(readFileSync(path)).digest('hex') === expectedSha) {
            return true;
          }
        }
        return false;
      }, 'SHA-verified browser-origin download', 120000);
      if (fixtureGetCount < 1) {
        throw Error('Browser add was acknowledged without a real HTTP data GET');
      }
      captureVerified = true;
    } finally {
      await new Promise(resolve => fixtureServer.close(resolve));
    }
    if (!captureVerified) throw Error('Browser download capture was not SHA verified');

    writeFileSync(join(evidenceDir, 'chrome-native-extension-report.json'), JSON.stringify({
      browser: 'Google Chrome', extensionId, manifestVersion: manifest.manifest_version,
      nativeHost: hostName, realChromeExtensionLoaded: true, requests: results,
      browserDownloadCaptured: captureVerified, browserDownloadSha256Verified: captureVerified,
      browserFixtureBytes: fixtureBytes.length, browserFixtureGetRequests: fixtureGetCount,
      connectedPort: firstPort,
      reconnectedPort, verified: true,
    }, null, 2));
    console.log('PASS: real Chrome extension -> installed HyperGet native host; three one-shot replies, three connected-port replies, one reconnect and one SHA-verified browser download');
  } finally { page.close(); }
} finally {
  browserSocket?.close();
  if (chrome?.pid) {
    spawnSync('taskkill.exe', ['/PID', String(chrome.pid), '/T', '/F'],
      { stdio: 'ignore', windowsHide: true });
  }
}
