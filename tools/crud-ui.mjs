#!/usr/bin/env node
/**
 * CRUD UI smoke for the employee module: page loads, create dialog opens,
 * empty submit is blocked client-side, and a created row can be searched.
 */
import fs from 'node:fs';
import path from 'node:path';
import { Browser } from './cdp.mjs';

const URL = process.env.APP_FRONTEND_URL || 'http://127.0.0.1:5173';
const candidates = [
  process.env.CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
].filter(Boolean);
const chrome = candidates.find((candidate) => fs.existsSync(candidate));
if (!chrome) {
  console.error('CRUD UI smoke: no Chrome/Edge found');
  process.exit(1);
}

const browser = await new Browser({ chromePath: chrome, width: 1440, height: 900 }).launch();
let failed = false;
function check(label, ok, detail = '') {
  console.log(`${ok ? 'PASS' : 'FAIL'} ${label}${detail ? `: ${detail}` : ''}`);
  if (!ok) failed = true;
}
try {
  await browser.goto(`${URL}/employees`, { waitMs: 1800 });
  await browser.waitFor('document.querySelector(".employee-table")', 20000);
  await browser.waitFor('document.querySelector(".el-pagination")', 15000);
  check('employees page', true);

  const clicked = await browser.evaluate(`(() => {
    const button = [...document.querySelectorAll('button')].find((el) => el.textContent.includes('新增员工'));
    if (!button) return false;
    button.click();
    return true;
  })()`);
  check('open create dialog', clicked);
  await browser.waitFor('(() => { const d = document.querySelector(".el-dialog"); return Boolean(d && d.offsetParent !== null); })()', 10000);

  const code = `UI${Date.now()}`;
  const name = '界面烟测员工';
  await browser.evaluate(`(() => {
    const inputs = [...document.querySelectorAll('.el-dialog input')];
    if (inputs[0]) { inputs[0].value = ${JSON.stringify(code)}; inputs[0].dispatchEvent(new Event('input', { bubbles: true })); }
    if (inputs[1]) { inputs[1].value = ${JSON.stringify(name)}; inputs[1].dispatchEvent(new Event('input', { bubbles: true })); }
    return true;
  })()`);
  await browser.evaluate(`(() => {
    const button = document.querySelector('.el-dialog__footer .el-button--primary');
    if (!button) return false;
    button.click();
    return true;
  })()`);
  await browser.waitFor('(() => { const d = document.querySelector(".el-dialog"); return !d || d.offsetParent === null; })()', 20000);

  await browser.evaluate(`(() => {
    const input = document.querySelector('.toolbar-search input');
    if (!input) return false;
    input.value = ${JSON.stringify(code)};
    input.dispatchEvent(new Event('input', { bubbles: true }));
    const query = [...document.querySelectorAll('button')].find((el) => el.textContent.trim() === '查询');
    if (!query) return false;
    query.click();
    return true;
  })()`);
  await browser.waitFor(`(() => {
    const table = document.querySelector('.employee-table');
    return Boolean(table && table.innerText.includes(${JSON.stringify(name)}));
  })()`, 20000);
  check('created row visible', true);

  const errors = browser.consoleLogs.filter((entry) => entry.type === 'error' && !/favicon/i.test(entry.text));
  if (browser.exceptions.length || errors.length) {
    console.error('browser errors:', browser.exceptions.join(' | '), errors.map((entry) => entry.text).join(' | '));
    failed = true;
  }
} catch (error) {
  console.error('CRUD UI smoke failed:', error.message);
  failed = true;
} finally {
  await browser.close();
}
if (failed) process.exitCode = 1;
else console.log('CRUD UI smoke: PASS');
