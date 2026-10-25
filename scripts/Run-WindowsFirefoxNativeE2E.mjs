// Real Firefox WebExtension -> installed HyperGet native host -> real HTTP download.
// Uses the W3C WebDriver HTTP protocol directly; no Selenium/npm runtime dependency.
// Browser automation docs: Selenium Firefox add-on API and Mozilla WebExtension IDs.
import { createServer } from 'node:http';
import { request as httpRequest } from 'node:http';
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';

const ADDON_ID = 'browser-integration@personaldownloadmanager.local';
const NATIVE_HOST = 'app.personaldownloadmanager';
let currentStage = 'PREFLIGHT';
let lastWebDriverFailure = null;
const SOURCE_SHA = process.env.HYPERGET_TESTED_SHA || 'unknown';

function sleep(ms) { return new Promise(resolveSleep => setTimeout(resolveSleep, ms)); }

async function waitFor(check, label, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const value = await check();
      if (value) return value;
    } catch { /* service/profile may not be ready yet */ }
    await sleep(250);
  }
  throw new Error(label);
}

function safeWebDriverRoute(route) {
  if (route === '/status') return 'STATUS';
  if (route === '/session') return 'CREATE_SESSION';
  if (/^\/session\/[^/]+\/moz\/addon\/install$/.test(route)) return 'INSTALL_ADDON';
  if (/^\/session\/[^/]+\/url$/.test(route)) return 'NAVIGATE';
  if (/^\/session\/[^/]+\/execute\/(?:sync|async)$/.test(route)) return 'EXECUTE';
  if (/^\/session\/[^/]+$/.test(route)) return 'SESSION';
  return 'OTHER';
}
const SAFE_WEBDRIVER_ERRORS = new Set([
  'invalid argument','session not created','unknown command','unsupported operation',
  'no such window','no such session','invalid session id','timeout','script timeout',
  'javascript error','unknown error','element not interactable','invalid selector',
  'unexpected alert open','no such alert','stale element reference','detached shadow root',
  'move target out of bounds','insecure certificate','unable to set cookie',
  'unable to capture screen','no such element','element click intercepted','invalid cookie domain',
]);
function normalizedWebDriverError(value) {
  if (typeof value !== 'string') return 'HTTP_ERROR';
  if (!SAFE_WEBDRIVER_ERRORS.has(value)) return 'OTHER_W3C_ERROR';
  return value.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_|_$/g, '');
}
function recordWebDriverFailure(method, route, statusCode, errorCode) {
  lastWebDriverFailure = { method, routeClass: safeWebDriverRoute(route),
    statusCode: Number.isInteger(statusCode) ? statusCode : null, errorCode };
}
function webdriverRequest(port, route, method = 'GET', body = undefined, timeoutMs = 30000) {
  const serialized = body === undefined ? null : Buffer.from(JSON.stringify(body));
  return new Promise((resolveRequest, rejectRequest) => {
    const routeClass = safeWebDriverRoute(route);
    let localFailure = null;
    const req = httpRequest({
      host: '127.0.0.1', port, path: route, method,
      headers: serialized ? {'Content-Type':'application/json; charset=utf-8','Content-Length':String(serialized.length)} : {},
    }, response => {
      const chunks = [];
      response.on('data', chunk => chunks.push(chunk));
      response.on('end', () => {
        let parsed;
        try { parsed = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
        catch {
          recordWebDriverFailure(method, route, response.statusCode ?? 500, 'INVALID_JSON');
          rejectRequest(new Error('WEBDRIVER_INVALID_JSON')); return;
        }
        const statusCode = response.statusCode ?? 500;
        if (statusCode >= 400 || parsed?.value?.error) {
          const errorCode = normalizedWebDriverError(parsed?.value?.error);
          recordWebDriverFailure(method, route, statusCode, errorCode);
          rejectRequest(new Error(`WEBDRIVER_COMMAND_FAILED:${routeClass}:${statusCode}:${errorCode}`));
          return;
        }
        resolveRequest(parsed);
      });
    });
    req.setTimeout(timeoutMs, () => {
      localFailure = 'TIMEOUT'; recordWebDriverFailure(method, route, null, localFailure);
      req.destroy(new Error('WEBDRIVER_TIMEOUT'));
    });
    req.on('error', () => {
      if (!localFailure) { localFailure = 'UNAVAILABLE'; recordWebDriverFailure(method, route, null, localFailure); }
      rejectRequest(new Error(localFailure === 'TIMEOUT' ? 'WEBDRIVER_TIMEOUT' : 'WEBDRIVER_UNAVAILABLE'));
    });
    if (serialized) req.write(serialized);
    req.end();
  });
}

async function reservePort() {
  const server = createServer();
  await new Promise((resolveListen, rejectListen) => {
    server.once('error', rejectListen);
    server.listen(0, '127.0.0.1', resolveListen);
  });
  const port = server.address().port;
  await new Promise(resolveClose => server.close(resolveClose));
  return port;
}

function readExtensionUuid(profileDir) {
  const prefsPath = join(profileDir, 'prefs.js');
  if (!existsSync(prefsPath)) return null;
  const prefs = readFileSync(prefsPath, 'utf8');
  const match = /user_pref\("extensions\.webextensions\.uuids",\s*("(?:\\.|[^"\\])*")\s*\);/.exec(prefs);
  if (!match) return null;
  try {
    const uuidMap = JSON.parse(JSON.parse(match[1]));
    const uuid = uuidMap?.[ADDON_ID];
    return typeof uuid === 'string' && /^[a-f0-9-]{16,64}$/i.test(uuid) ? uuid : null;
  } catch { return null; }
}

async function executeScript(port, sessionId, script, args = [], async = false) {
  const route = `/session/${sessionId}/execute/${async ? 'async' : 'sync'}`;
  const result = await webdriverRequest(port, route, 'POST', { script, args }, 30000);
  return result.value;
}

async function nativeMessage(port, sessionId, message) {
  const script = `
    const done = arguments[arguments.length - 1];
    const host = arguments[0];
    const request = arguments[1];
    try {
      if (typeof browser === 'undefined' || !browser.runtime ||
          typeof browser.runtime.sendNativeMessage !== 'function') {
        done({ kind: 'NO_EXTENSION_CONTEXT' });
        return;
      }
      browser.runtime.sendNativeMessage(host, request)
        .then(reply => done({ reply }))
        .catch(() => done({ kind: 'NATIVE_REJECTED' }));
    } catch (_) {
      done({ kind: 'NATIVE_THROW' });
    }
  `;
  return executeScript(port, sessionId, script, [NATIVE_HOST, message], true);
}

function safeErrorType(result) {
  if (result?.kind) return result.kind;
  const payload = result?.reply?.content?.payload;
  try {
    const value = JSON.parse(payload);
    if (value?.errorType === 'INVALID_REQUEST' || value?.errorType === 'REQUEST_FAILED') {
      return value.errorType;
    }
  } catch { /* never expose native error text or payload */ }
  return 'UNCLASSIFIED';
}

function createFixture() {
  const fileName = `hyperget-firefox-capture-${process.pid}.bin`;
  const bytes = Buffer.alloc(2 * 1024 * 1024);
  for (let i = 0; i < bytes.length; i++) bytes[i] = i % 251;
  const expectedSha = createHash('sha256').update(bytes).digest('hex');
  let getRequests = 0;
  const server = createServer((req, res) => {
    const pathname = new URL(req.url ?? '/', 'http://127.0.0.1').pathname;
    if (pathname !== '/' + fileName) { res.writeHead(404); res.end(); return; }
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); res.end(); return; }
    const range = req.headers.range;
    const match = range ? /^bytes=(\d+)-(\d*)$/.exec(range) : null;
    if (range && !match) {
      res.writeHead(416, { 'Content-Range': `bytes */${bytes.length}` }); res.end(); return;
    }
    const start = match ? Number(match[1]) : 0;
    const end = match && match[2] ? Number(match[2]) : bytes.length - 1;
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) ||
        start < 0 || start >= bytes.length || end < start || end >= bytes.length) {
      res.writeHead(416, { 'Content-Range': `bytes */${bytes.length}` }); res.end(); return;
    }
    const headers = {
      'Content-Type': 'application/octet-stream',
      'Content-Length': String(end - start + 1),
      'Content-Disposition': `attachment; filename="${fileName}"`,
      'Accept-Ranges': 'bytes',
      'Cache-Control': 'no-store',
    };
    if (match) headers['Content-Range'] = `bytes ${start}-${end}/${bytes.length}`;
    res.writeHead(match ? 206 : 200, headers);
    if (req.method === 'GET') {
      getRequests++;
      res.end(bytes.subarray(start, end + 1));
    } else res.end();
  });
  return { server, fileName, bytes, expectedSha, getRequests: () => getRequests };
}

function findCapturedFile(directory, fileName, depth = 3) {
  if (!existsSync(directory)) return null;
  let entries;
  try { entries = readdirSync(directory, { withFileTypes: true }); }
  catch { return null; }
  for (const entry of entries) {
    const child = join(directory, entry.name);
    if (entry.isFile() && entry.name === fileName) return child;
    if (entry.isDirectory() && depth > 0 && !entry.name.startsWith('.')) {
      const found = findCapturedFile(child, fileName, depth - 1);
      if (found) return found;
    }
  }
  return null;
}

const [firefoxExeArg, geckodriverArg, addonZipArg, evidenceArg] = process.argv.slice(2);
if (!firefoxExeArg || !geckodriverArg || !addonZipArg || !evidenceArg) {
  throw new Error('Expected Firefox executable, geckodriver, Firefox add-on ZIP and evidence directory');
}
const firefoxExe = resolve(firefoxExeArg);
const geckodriverExe = resolve(geckodriverArg);
const addonZip = resolve(addonZipArg);
const evidenceDir = resolve(evidenceArg);
for (const path of [firefoxExe, geckodriverExe, addonZip]) {
  if (!existsSync(path)) throw new Error('Firefox E2E input missing');
}
mkdirSync(evidenceDir, { recursive: true });

const guiExe = process.env.HYPERGET_GUI_EXE;
if (!guiExe || !existsSync(guiExe)) throw new Error('Installed HyperGet GUI missing');
const cliExe = join(dirname(guiExe), 'DownloadManagerCli.exe');
if (!existsSync(cliExe)) throw new Error('Installed HyperGet CLI missing');
const warmup = spawnSync(cliExe,
  ['download', 'show', '--status-lines', '9223372036854775807'],
  { encoding: 'utf8', timeout: 45000, windowsHide: true });
if (warmup.error || warmup.status !== 0 || warmup.stdout?.trim()) {
  throw new Error('Installed HyperGet GUI/IPC readiness check failed');
}

const fixture = createFixture();
await new Promise((resolveListen, rejectListen) => {
  fixture.server.once('error', rejectListen);
  fixture.server.listen(0, '127.0.0.1', resolveListen);
});
const fixturePort = fixture.server.address().port;
const geckoPort = await reservePort();
const gecko = spawn(geckodriverExe,
  ['--host', '127.0.0.1', '--port', String(geckoPort), '--log', 'error'],
  { stdio: 'ignore', windowsHide: true });
let sessionId = null;
let profileDir = null;
let addonInstalled = false;
let downloadVerified = false;
let nativePingVerified = false;
let safeErrorVerified = false;
let firefoxVersion = 'unknown';

function safeFailureCode(error) {
  const message = error instanceof Error ? error.message : '';
  if (/^WEBDRIVER_COMMAND_FAILED:[A-Z_]+:\\d{3}:[A-Z_]+$/.test(message)) return message;
  if (/^[A-Z0-9_:-]{1,120}$/.test(message)) return message;
  return 'UNCLASSIFIED';
}
function writeEvidenceReport(verified, failureCode = null) {
  writeFileSync(join(evidenceDir, 'firefox-native-extension-report.json'), JSON.stringify({
    sourceSha: SOURCE_SHA, browser: 'Mozilla Firefox', firefoxVersion,
    extensionId: ADDON_ID, manifestVersion: 2, nativeHost: NATIVE_HOST,
    stage: currentStage, failureCode, webdriverFailure: lastWebDriverFailure,
    addonInstalled, nativePingCorrelated: nativePingVerified,
    safeErrorCorrelatedAndSanitized: safeErrorVerified,
    browserDownloadCaptured: downloadVerified, browserDownloadSha256Verified: downloadVerified,
    browserFixtureBytes: fixture.bytes.length, browserFixtureSha256: fixture.expectedSha,
    browserFixtureGetRequests: fixture.getRequests(), verified,
  }, null, 2));
}

try {
  currentStage = 'GECKODRIVER_STATUS';
  await waitFor(async () => {
    if (gecko.exitCode !== null) throw new Error('GECKODRIVER_EXITED');
    const response = await webdriverRequest(geckoPort, '/status', 'GET', undefined, 2000);
    return response?.value?.ready === true;
  }, 'Geckodriver did not become ready', 30000);

  currentStage = 'CREATE_FIREFOX_SESSION';
  const created = await webdriverRequest(geckoPort, '/session', 'POST', {
    capabilities: {
      alwaysMatch: {
        browserName: 'firefox',
        'moz:firefoxOptions': {
          binary: firefoxExe,
          args: ['-headless'],
          prefs: {
            'browser.shell.checkDefaultBrowser': false,
            'browser.startup.homepage_override.mstone': 'ignore',
            'browser.startup.homepage': 'about:blank',
            'browser.aboutwelcome.enabled': false,
            'app.update.auto': false,
            'app.update.enabled': false,
          },
        },
      },
    },
  }, 60000);
  sessionId = created?.value?.sessionId;
  const capabilities = created?.value?.capabilities;
  if (!sessionId || !capabilities) throw new Error('FIREFOX_SESSION_CREATE_FAILED');
  firefoxVersion = String(capabilities.browserVersion || 'unknown');
  profileDir = capabilities['moz:profile'];
  if (!profileDir || !existsSync(profileDir)) throw new Error('FIREFOX_PROFILE_NOT_FOUND');

  currentStage = 'INSTALL_FIREFOX_ADDON';
  const addonBase64 = readFileSync(addonZip).toString('base64');
  const installed = await webdriverRequest(geckoPort,
    `/session/${sessionId}/moz/addon/install`, 'POST',
    { addon: addonBase64, temporary: true }, 60000);
  if (installed?.value !== ADDON_ID) throw new Error('FIREFOX_ADDON_ID_MISMATCH');
  addonInstalled = true;

  currentStage = 'READ_EXTENSION_UUID';
  const uuid = await waitFor(() => readExtensionUuid(profileDir),
    'FIREFOX_EXTENSION_UUID_NOT_FOUND', 15000);
  const optionsUrl = `moz-extension://${uuid}/src/optionsui/option-ui.html`;
  currentStage = 'NAVIGATE_EXTENSION_OPTIONS';
  await webdriverRequest(geckoPort, `/session/${sessionId}/url`, 'POST', { url: optionsUrl });
  currentStage = 'WAIT_EXTENSION_CONTEXT';
  await waitFor(async () => {
    const state = await executeScript(geckoPort, sessionId,
      'return document.readyState === "complete" && typeof browser !== "undefined" && !!browser.runtime;');
    return state === true;
  }, 'FIREFOX_EXTENSION_PAGE_NOT_READY', 30000);

  currentStage = 'NATIVE_PING';
  const pingId = `B_firefox_ping_${process.pid}`;
  const ping = await nativeMessage(geckoPort, sessionId, {
    id: pingId,
    content: { action: 'ping', isError: false, payload: 'null' },
  });
  if (ping?.reply?.id !== pingId || ping.reply.content?.isError !== false ||
      ping.reply.content?.payload !== 'true') {
    throw new Error('FIREFOX_NATIVE_PING_FAILED');
  }
  nativePingVerified = true;

  currentStage = 'NATIVE_SAFE_ERROR';
  const errorId = `B_firefox_error_${process.pid}`;
  const safeError = await nativeMessage(geckoPort, sessionId, {
    id: errorId,
    content: { action: 'hyperget-nonexistent-action', isError: false, payload: 'null' },
  });
  let errorPayload;
  try { errorPayload = JSON.parse(safeError?.reply?.content?.payload); } catch { errorPayload = null; }
  if (safeError?.reply?.id !== errorId || safeError.reply.content?.isError !== true ||
      errorPayload?.errorType !== 'REQUEST_FAILED' ||
      errorPayload?.message !== 'HyperGet could not complete the browser request.') {
    throw new Error('FIREFOX_SAFE_ERROR_CHECK_FAILED:' + safeErrorType(safeError));
  }
  safeErrorVerified = true;

  currentStage = 'NATIVE_ADD';
  const downloadId = `B_firefox_download_${process.pid}`;
  const addRequest = {
    id: downloadId,
    content: {
      action: 'add',
      isError: false,
      payload: JSON.stringify({
        items: [{
          link: `http://127.0.0.1:${fixturePort}/${fixture.fileName}`,
          downloadPage: null, headers: null, description: null,
          suggestedName: fixture.fileName, type: 'http',
        }],
        options: { silentAdd: true, silentStart: true },
      }),
    },
  };
  const add = await nativeMessage(geckoPort, sessionId, addRequest);
  if (add?.reply?.id !== downloadId) {
    throw new Error('FIREFOX_ADD_REPLY_ID_MISMATCH:' + safeErrorType(add));
  }
  if (add.reply.content?.isError === true) {
    throw new Error('FIREFOX_ADD_REJECTED:' + safeErrorType(add));
  }
  if (add.reply.content?.isError !== false || add.reply.content?.payload !== 'true') {
    throw new Error('FIREFOX_ADD_ACK_UNEXPECTED');
  }

  const roots = ['Downloads', 'Desktop', 'Documents'].map(name => join(homedir(), name));
  currentStage = 'VERIFY_DOWNLOAD_SHA256';
  await waitFor(() => {
    for (const root of roots) {
      const path = findCapturedFile(root, fixture.fileName);
      if (!path) continue;
      try {
        if (statSync(path).size !== fixture.bytes.length) continue;
        const actualSha = createHash('sha256').update(readFileSync(path)).digest('hex');
        if (actualSha === fixture.expectedSha) return { sha256: actualSha };
      } catch { /* a live transfer may still be writing */ }
    }
    return null;
  }, 'FIREFOX_BROWSER_DOWNLOAD_SHA256_MISMATCH_OR_TIMEOUT', 120000);
  if (fixture.getRequests() < 1) throw new Error('FIREFOX_ADD_ACK_WITHOUT_REAL_HTTP_GET');
  downloadVerified = true;

  currentStage = 'COMPLETE';
  writeEvidenceReport(true, null);
  console.log('PASS: real Firefox extension -> installed HyperGet host; correlated ping, sanitized error and 2 MiB SHA-verified download');
} catch (error) {
  const failureCode = safeFailureCode(error);
  try { writeEvidenceReport(false, failureCode); } catch { /* preserve original failure */ }
  console.error(JSON.stringify({
    event: 'FIREFOX_NATIVE_E2E_FAILED', stage: currentStage,
    failureCode, webdriverFailure: lastWebDriverFailure,
  }));
  throw new Error(`FIREFOX_NATIVE_E2E_FAILED:${currentStage}:${failureCode}`);
} finally {
  await new Promise(resolveClose => fixture.server.close(resolveClose));
  if (sessionId) {
    try { await webdriverRequest(geckoPort, `/session/${sessionId}`, 'DELETE', undefined, 10000); }
    catch { /* cleanup only; acceptance result is already determined */ }
  }
  if (gecko.exitCode === null) {
    gecko.kill();
    await Promise.race([new Promise(resolveExit => gecko.once('exit', resolveExit)), sleep(3000)]);
  }
}
