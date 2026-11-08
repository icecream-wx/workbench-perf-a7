#!/usr/bin/env node
import { execFileSync, spawn } from 'node:child_process';
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const IS_WIN = process.platform === 'win32';
const RUN_DIR = path.join(ROOT, 'run');
const LOG_DIR = path.join(ROOT, 'logs');
const BACKEND_DIR = path.join(ROOT, 'backend');
const FRONTEND_DIR = path.join(ROOT, 'frontend');
const STATE_FILE = path.join(RUN_DIR, 'processes.json');
const DEFAULT_BACKEND_PORT = Number('8080') || 8080;
const DEFAULT_FRONTEND_PORT = Number('5173') || 5173;

const args = process.argv.slice(2);
const action = (args[0] || 'status').toLowerCase();
const mode = (args[1] || process.env.APP_MODE || 'local').toLowerCase();
const backendPort = Number(process.env.BACKEND_PORT || DEFAULT_BACKEND_PORT);
const frontendPort = Number(process.env.FRONTEND_PORT || (mode === 'prod' ? 4173 : DEFAULT_FRONTEND_PORT));

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function ensureDirs() {
  fs.mkdirSync(RUN_DIR, { recursive: true });
  fs.mkdirSync(LOG_DIR, { recursive: true });
}

function normalize(value) {
  return String(value || '').replace(/\\/g, '/').toLowerCase();
}

function pidAlive(pid) {
  if (!pid) return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

function commandLine(pid) {
  try {
    if (IS_WIN) {
      return execFileSync(
        'powershell',
        ['-NoProfile', '-Command', `(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").CommandLine`],
        { encoding: 'utf8', windowsHide: true },
      ).trim();
    }
    return fs.readFileSync(`/proc/${pid}/cmdline`, 'utf8').replace(/\0/g, ' ').trim();
  } catch {
    return '';
  }
}

function isOwned(pid, marker) {
  if (!pidAlive(pid) || !marker) return false;
  const line = normalize(commandLine(pid));
  return line.includes(normalize(ROOT)) && line.includes(normalize(marker));
}

function commandExists(name) {
  try {
    execFileSync(IS_WIN ? 'where.exe' : 'which', [name], { stdio: 'ignore', windowsHide: true });
    return true;
  } catch {
    return false;
  }
}

function readState() {
  try {
    const state = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
    return { backend: state.backend || null, frontend: state.frontend || null, mode: state.mode || null };
  } catch {
    return { backend: null, frontend: null, mode: null };
  }
}

function saveState(state) {
  ensureDirs();
  fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2) + '\n', 'utf8');
}

function writePid(name, pid) {
  ensureDirs();
  fs.writeFileSync(path.join(RUN_DIR, `${name}.pid`), `${pid}\n`, 'utf8');
}

function cleanState() {
  for (const file of [STATE_FILE, path.join(RUN_DIR, 'backend.pid'), path.join(RUN_DIR, 'frontend.pid')]) {
    try {
      fs.unlinkSync(file);
    } catch {
      /* already gone */
    }
  }
}

function tail(name) {
  const files = [path.join(LOG_DIR, `${name}.log`), path.join(LOG_DIR, `${name}.err.log`)];
  for (const file of files) {
    if (!fs.existsSync(file)) continue;
    const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/).filter(Boolean).slice(-20);
    if (!lines.length) continue;
    console.error(`--- ${path.relative(ROOT, file)} ---`);
    for (const line of lines) console.error(line);
  }
}

function portOpen(port) {
  return new Promise((resolve) => {
    const socket = net.connect({ host: '127.0.0.1', port });
    let settled = false;
    const finish = (value) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve(value);
    };
    socket.setTimeout(800);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
  });
}

async function fetchStatus(url) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(2500) });
    return { ok: response.ok, status: response.status, body: await response.text() };
  } catch {
    return { ok: false, status: 0, body: '' };
  }
}

async function backendHealthy() {
  const result = await fetchStatus(`http://127.0.0.1:${backendPort}/actuator/health`);
  return result.ok && /"status"\s*:\s*"UP"/.test(result.body);
}

async function frontendHealthy() {
  const result = await fetchStatus(`http://127.0.0.1:${frontendPort}/`);
  return result.ok;
}

function shQuote(value) {
  return `'${String(value).replace(/'/g, `'\\''`)}'`;
}

function cmdQuote(value) {
  return `"${String(value)}"`;
}

function findBackendJar() {
  const target = path.join(BACKEND_DIR, 'target');
  if (!fs.existsSync(target)) throw new Error('backend/target 不存在，请先执行 build');
  const jars = fs.readdirSync(target)
    .filter((name) => name.endsWith('.jar') && !name.endsWith('.original'))
    .map((name) => path.join(target, name))
    .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
  if (!jars.length) throw new Error('backend/target 下没有 jar，请先执行 build');
  return jars[0];
}

function backendCommand() {
  if (mode === 'prod') {
    const jar = findBackendJar();
    const relative = path.relative(BACKEND_DIR, jar);
    return `java -jar ${cmdQuote(relative)} --spring.profiles.active=prod,mysql`;
  }
  if (!['local', 'dev'].includes(mode)) throw new Error(`未知模式：${mode}（可选 local/dev/prod）`);
  const profiles = mode === 'local' ? 'local' : 'dev';
  return `mvn -q spring-boot:run -Dspring-boot.run.profiles=${profiles}`;
}

function frontendCommand() {
  if (mode === 'prod') return `npm run preview -- --host 127.0.0.1 --port ${frontendPort}`;
  return `npm run dev -- --host 127.0.0.1 --port ${frontendPort}`;
}

function spawnService(name, command, cwd, extraEnv = {}) {
  ensureDirs();
  const logFile = path.join(LOG_DIR, `${name}.log`);
  const errFile = path.join(LOG_DIR, `${name}.err.log`);
  const outFd = fs.openSync(logFile, 'a');
  const errFd = fs.openSync(errFile, 'a');
  fs.writeSync(outFd, `\n=== ${new Date().toISOString()} ${name} start: ${command}\n`);
  const env = { ...process.env, ...extraEnv };
  let child;
  if (IS_WIN) {
    const line = `cd /d ${cmdQuote(cwd)} && ${command}`;
    child = spawn('cmd.exe', ['/d', '/s', '/c', line], {
      cwd: ROOT,
      env,
      windowsHide: true,
      detached: true,
      windowsVerbatimArguments: true,
      stdio: ['ignore', outFd, errFd],
    });
  } else {
    const line = `cd ${shQuote(cwd)} && { ${command}; }; status=$?; exit $status`;
    child = spawn('/bin/bash', ['-lc', line], {
      cwd: ROOT,
      env,
      detached: true,
      stdio: ['ignore', outFd, errFd],
    });
  }
  fs.closeSync(outFd);
  fs.closeSync(errFd);
  if (!child.pid) throw new Error(`无法启动 ${name}`);
  child.unref();
  return child;
}

async function waitFor(name, child, healthy, timeoutMs = 180000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (child.exitCode !== null || !pidAlive(child.pid)) return false;
    if (await healthy()) return true;
    await sleep(1000);
  }
  return false;
}

function killTree(pid, force) {
  if (!pid) return;
  try {
    if (IS_WIN) {
      execFileSync('taskkill', ['/PID', String(pid), '/T', ...(force ? ['/F'] : [])], { windowsHide: true, stdio: 'ignore' });
    } else {
      const signal = force ? 'SIGKILL' : 'SIGTERM';
      try {
        process.kill(-pid, signal);
      } catch {
        process.kill(pid, signal);
      }
    }
  } catch {
    /* process may already be gone */
  }
}

async function stopRecord(name, record) {
  if (!record) return;
  if (!isOwned(record.pid, record.marker)) {
    console.log(`[WARN] ${name} pid ${record.pid} 不属于本应用或已退出，不结束它`);
    return;
  }
  console.log(`[INFO] stopping ${name} (pid ${record.pid})`);
  killTree(record.pid, false);
  const waitSeconds = IS_WIN ? 5 : 15;
  for (let index = 0; index < waitSeconds; index += 1) {
    if (!pidAlive(record.pid)) return;
    await sleep(1000);
  }
  if (pidAlive(record.pid)) {
    console.log(`[WARN] ${name} 未在 ${waitSeconds} 秒内退出，强制结束`);
    killTree(record.pid, true);
  }
}

async function stop(state = readState()) {
  await stopRecord('backend', state.backend);
  await stopRecord('frontend', state.frontend);
  cleanState();
}

async function start() {
  if (!['local', 'dev', 'prod'].includes(mode)) {
    throw new Error(`未知模式：${mode}（可选 local/dev/prod）`);
  }
  if (mode !== 'local' && !process.env.NACOS_ADDR) {
    throw new Error(`${mode} 模式需要 NACOS_ADDR（local 模式不需要 Nacos）`);
  }
  if (mode === 'prod' && !process.env.DB_HOST) {
    throw new Error('prod 模式需要 DB_HOST（MySQL 8.x）');
  }
  for (const command of (mode === 'prod' ? ['java', 'npm'] : ['mvn', 'npm'])) {
    if (!commandExists(command)) throw new Error(`找不到命令：${command}`);
  }
  if (!fs.existsSync(path.join(FRONTEND_DIR, 'node_modules'))) {
    throw new Error('frontend/node_modules 不存在，请先执行 build');
  }
  if (mode === 'prod' && !fs.existsSync(path.join(FRONTEND_DIR, 'dist', 'index.html'))) {
    throw new Error('frontend/dist 不存在，请先执行 build');
  }

  const state = readState();
  const backendOwn = state.backend && isOwned(state.backend.pid, state.backend.marker);
  const frontendOwn = state.frontend && isOwned(state.frontend.pid, state.frontend.marker);
  if (backendOwn && frontendOwn) {
    if (await backendHealthy() && await frontendHealthy()) {
      console.log('[INFO] already running');
      console.log(`[INFO] backend:  http://127.0.0.1:${backendPort}`);
      console.log(`[INFO] frontend: http://127.0.0.1:${frontendPort}`);
      return;
    }
  }
  if (backendOwn || frontendOwn) {
    throw new Error('检测到不完整的运行状态；请先执行 stop');
  }
  cleanState();

  if (await portOpen(backendPort)) throw new Error(`后端端口 ${backendPort} 已被占用`);
  if (await portOpen(frontendPort)) throw new Error(`前端端口 ${frontendPort} 已被占用`);

  const nextState = {
    mode,
    backend: null,
    frontend: null,
    startedAt: new Date().toISOString(),
  };
  saveState(nextState);

  try {
    const backend = spawnService('backend', backendCommand(), BACKEND_DIR, {
      SERVER_PORT: String(backendPort),
      BACKEND_PORT: String(backendPort),
      APP_MODE: mode,
    });
    nextState.backend = {
      pid: backend.pid,
      marker: mode === 'prod' ? 'java' : 'mvn',
      startedAt: new Date().toISOString(),
    };
    writePid('backend', backend.pid);
    saveState(nextState);
    console.log(`[INFO] backend starting (pid ${backend.pid}, profile ${mode === 'prod' ? 'prod,mysql' : mode === 'local' ? 'local' : 'dev'})`);
    if (!await waitFor('backend', backend, backendHealthy)) {
      throw new Error('后端健康检查未通过（/actuator/health 未返回 UP）');
    }
    console.log(`[INFO] backend ready: http://127.0.0.1:${backendPort}`);

    const frontend = spawnService('frontend', frontendCommand(), FRONTEND_DIR, {
      BACKEND_PORT: String(backendPort),
      SERVER_PORT: String(backendPort),
      APP_MODE: mode,
    });
    nextState.frontend = {
      pid: frontend.pid,
      marker: 'npm',
      startedAt: new Date().toISOString(),
    };
    writePid('frontend', frontend.pid);
    saveState(nextState);
    console.log(`[INFO] frontend starting (pid ${frontend.pid})`);
    if (!await waitFor('frontend', frontend, frontendHealthy)) {
      throw new Error('前端健康检查未通过（首页未返回 200）');
    }
    console.log(`[INFO] frontend ready: http://127.0.0.1:${frontendPort}`);
    console.log(`[INFO] logs: ${LOG_DIR}`);
    console.log(`[INFO] stop: run stop.cmd / stop.sh`);
  } catch (error) {
    console.error(`[ERROR] ${error.message}`);
    tail('backend');
    tail('frontend');
    await stop(nextState);
    process.exitCode = 1;
  }
}

async function status() {
  const state = readState();
  console.log(`[INFO] mode=${state.mode || '-'} backendPort=${backendPort} frontendPort=${frontendPort}`);
  console.log(`[INFO] backend pid=${state.backend?.pid || '-'} owned=${Boolean(state.backend && isOwned(state.backend.pid, state.backend.marker))} healthy=${await backendHealthy()}`);
  console.log(`[INFO] frontend pid=${state.frontend?.pid || '-'} owned=${Boolean(state.frontend && isOwned(state.frontend.pid, state.frontend.marker))} healthy=${await frontendHealthy()}`);
}

try {
  if (action === 'start') await start();
  else if (action === 'stop') await stop();
  else if (action === 'status') await status();
  else {
    console.error(`usage: node scripts/app.mjs start [local|dev|prod] | stop | status`);
    process.exitCode = 2;
  }
} catch (error) {
  console.error(`[ERROR] ${error.message}`);
  process.exitCode = 1;
}
