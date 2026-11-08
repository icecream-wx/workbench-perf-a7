#!/usr/bin/env node
/**
 * Generic UI smoke via Chrome DevTools Protocol (no npm dependency).
 * Opens the frontend, checks the SPA mounted, and reports console errors.
 * Business-specific flows should be added by the agent in tools/.
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
  if (process.env.SKIP_UI_SMOKE === '1') {
    console.log('UI smoke: SKIP (no Chrome/Edge, SKIP_UI_SMOKE=1)');
    process.exit(0);
  }
  console.error('UI smoke: no Chrome/Edge found; set CHROME_PATH or SKIP_UI_SMOKE=1');
  process.exit(1);
}

const browser = await new Browser({ chromePath: chrome, width: 1440, height: 900 }).launch();
let failed = false;
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
  if (!info.title && !info.text) failed = true;
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
