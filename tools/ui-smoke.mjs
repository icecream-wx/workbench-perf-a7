#!/usr/bin/env node
/**
 * Generic UI smoke via Chrome DevTools Protocol (no npm dependency) plus the
 * employee page cases required by the batch: table rows, create dialog and
 * client-side validation on empty submit.
 */
import fs from 'node:fs';
import path from 'node:path';
import { Browser } from './cdp.mjs';

const URL = process.env.APP_FRONTEND_URL || 'http://127.0.0.1:5173';
const BACKEND = process.env.APP_BACKEND_URL || 'http://127.0.0.1:8080';
const candidates = [
  process.env.CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
].filter(Boolean);
const chrome = candidates.find((candidate) => fs.existsSync(candidate));

if (!chrome) {
  if (process.env.SKIP_UI_SMOKE === '1') {
    console.log('UI smoke: SKIP (no Chrome/Edge, SKIP_UI_SMOKE=1)');
    process.exit(0);
  }
  console.error('UI smoke: no Chrome/Edge found; set CHROME_PATH or SKIP_UI_SMOKE=1');
  process.exit(1);
}

let failed = false;
function check(label, ok, detail = '') {
  console.log(`${ok ? 'PASS' : 'FAIL'} ${label}${detail ? `: ${detail}` : ''}`);
  if (!ok) failed = true;
}

async function seedEmployee() {
  try {
    const response = await fetch(`${BACKEND}/api/employees`, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({
        employeeNo: `UISEED${Date.now()}`,
        name: '界面预置员工',
        gender: 'UNKNOWN',
        department: '研发部',
        hireDate: '2024-01-31',
        status: 'ACTIVE',
      }),
    });
    return response.status === 201;
  } catch {
    return false;
  }
}

const browser = await new Browser({ chromePath: chrome, width: 1440, height: 900 }).launch();
try {
  await browser.goto(URL, { waitMs: 1500 });
  await browser.waitFor('document.querySelector("#app")', 15000);
  await browser.waitFor('document.querySelector("#app").textContent.trim().length > 0', 15000);
  const info = await browser.evaluate(`({
    title: document.title,
    url: location.href,
    text: (document.querySelector('#app')?.innerText || '').trim().slice(0, 120),
  })`);
  console.log('UI:', JSON.stringify(info));
  check('home mounted', Boolean(info.title || info.text));

  check('employee seed', await seedEmployee());

  await browser.goto(`${URL}/employees`, { waitMs: 1500 });
  await browser.waitFor('document.querySelector(".employee-table")', 20000);
  await browser.waitFor('document.querySelectorAll(".el-table__row").length > 0', 20000);
  check('employees page table rows', true);

  const clicked = await browser.evaluate(`(() => {
    const button = [...document.querySelectorAll('button')].find((el) => el.textContent.includes('新增员工'));
    if (!button) return false;
    button.click();
    return true;
  })()`);
  check('open create dialog', clicked);
  await browser.waitFor('(() => { const d = document.querySelector(".el-dialog"); return Boolean(d && d.offsetParent !== null); })()', 10000);

  await browser.evaluate(`(() => {
    const button = document.querySelector('.el-dialog__footer .el-button--primary');
    if (!button) return false;
    button.click();
    return true;
  })()`);
  await browser.waitFor('document.querySelectorAll(".el-form-item__error").length > 0', 10000);
  const formError = await browser.evaluate(`document.querySelector('.el-form-item__error')?.innerText || ''`);
  check('empty submit shows validation', /工号不能为空|姓名不能为空/.test(formError), formError);

  await browser.evaluate(`(() => {
    const button = [...document.querySelectorAll('.el-dialog__footer button')].find((el) => el.textContent.includes('取消'));
    if (!button) return false;
    button.click();
    return true;
  })()`);
  await browser.waitFor('(() => { const d = document.querySelector(".el-dialog"); return !d || d.offsetParent === null; })()', 10000);

  if (process.env.UI_SMOKE_SCREENSHOT) {
    const file = path.resolve('verify', 'ui-smoke.png');
    fs.mkdirSync(path.dirname(file), { recursive: true });
    await browser.screenshot(file);
    console.log('screenshot:', file);
  }
  const errors = browser.consoleLogs.filter((entry) => entry.type === 'error' && !/favicon/i.test(entry.text));
  if (browser.exceptions.length || errors.length) {
    console.error('browser errors:', browser.exceptions.join(' | '), errors.map((entry) => entry.text).join(' | '));
    failed = true;
  }
} catch (error) {
  console.error('UI smoke failed:', error.message);
  failed = true;
} finally {
  await browser.close();
}
if (failed) process.exitCode = 1;
else console.log('UI smoke: PASS');
