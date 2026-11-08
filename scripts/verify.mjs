#!/usr/bin/env node
/**
 * One-shot local verification: start once, run API/UI smoke, stop once.
 * Build first with build.cmd/build.sh. Business-specific checks belong in tools/.
 */
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const APP = path.join(ROOT, 'scripts', 'app.mjs');
const BACKEND = process.env.APP_BACKEND_URL || 'http://127.0.0.1:8080';
const FRONTEND = process.env.APP_FRONTEND_URL || 'http://127.0.0.1:5173';

function run(label, args, env = {}) {
  console.log(`\n=== ${label} ===`);
  const result = spawnSync(process.execPath, args, {
    cwd: ROOT,
    stdio: 'inherit',
    windowsHide: true,
    env: { ...process.env, ...env },
  });
  return result.status ?? 1;
}

let failed = false;
const start = run('start local', [APP, 'start', 'local']);
if (start !== 0) {
  console.error('verify: start failed');
  failed = true;
} else {
  const api = run('api smoke', [path.join(ROOT, 'tools', 'api-smoke.mjs')], {
    APP_BACKEND_URL: BACKEND,
    APP_FRONTEND_URL: FRONTEND,
  });
  if (api !== 0) failed = true;
  const crudApi = path.join(ROOT, 'tools', 'crud-api.mjs');
  if (fs.existsSync(crudApi)) {
    const result = run('crud api', [crudApi], { APP_BACKEND_URL: BACKEND, APP_FRONTEND_URL: FRONTEND });
    if (result !== 0) failed = true;
  }
  const ui = run('ui smoke', [path.join(ROOT, 'tools', 'ui-smoke.mjs')], {
    APP_BACKEND_URL: BACKEND,
    APP_FRONTEND_URL: FRONTEND,
  });
  if (ui !== 0) failed = true;
  const crudUi = path.join(ROOT, 'tools', 'crud-ui.mjs');
  if (fs.existsSync(crudUi)) {
    const result = run('crud ui', [crudUi], { APP_BACKEND_URL: BACKEND, APP_FRONTEND_URL: FRONTEND });
    if (result !== 0) failed = true;
  }
}

const stop = run('stop', [APP, 'stop']);
if (stop !== 0) failed = true;

if (failed) {
  console.error('\nverify: FAILED');
  process.exitCode = 1;
} else {
  console.log('\nverify: PASS');
}
