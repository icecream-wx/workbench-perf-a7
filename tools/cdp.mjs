/**
 * Minimal Chrome DevTools Protocol driver over --remote-debugging-pipe.
 * Node 20 has no global WebSocket, and the pipe transport needs no dependency,
 * so this keeps the verification tooling dependency-free.
 */
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

export class Browser {
  constructor({ chromePath, width = 1600, height = 1100 } = {}) {
    this.chromePath = chromePath;
    this.width = width;
    this.height = height;
    this.pending = new Map();
    this.seq = 0;
    this.buffer = '';
    this.sessionId = null;
    this.consoleLogs = [];
    this.exceptions = [];
  }

  async launch() {
    const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'wb-cdp-'));
    this.child = spawn(this.chromePath, [
      '--headless=new',
      '--remote-debugging-pipe',
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-gpu',
      '--hide-scrollbars',
      '--force-device-scale-factor=1',
      `--window-size=${this.width},${this.height}`,
      `--user-data-dir=${userDataDir}`,
      'about:blank',
    ], { stdio: ['ignore', 'pipe', 'pipe', 'pipe', 'pipe'], windowsHide: true });

    this.child.stderr.on('data', (chunk) => {
      const text = chunk.toString('utf8');
      if (/ERROR|FATAL/.test(text)) process.stderr.write(`[chrome] ${text.slice(0, 500)}`);
    });
    this.child.on('exit', (code) => {
      this.exited = true;
      if (code !== 0 && !this.closing) process.stderr.write(`[chrome] exited ${code}\n`);
    });
    const output = this.child.stdio[3];
    const input = this.child.stdio[4];
    input.on('data', (chunk) => this.onData(chunk));
    this.send = (method, params = {}) => new Promise((resolve, reject) => {
      const id = ++this.seq;
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`CDP timeout: ${method}`));
      }, 60000);
      this.pending.set(id, (message) => {
        clearTimeout(timer);
        if (message.error) reject(new Error(`${method}: ${message.error.message}`));
        else resolve(message.result);
      });
      output.write(JSON.stringify({ id, method, params }) + '\0');
    });

    await this.send('Browser.getVersion');
    const { targetId } = await this.send('Target.createTarget', { url: 'about:blank' });
    const attached = await this.send('Target.attachToTarget', { targetId, flatten: true });
    this.sessionId = attached.sessionId;
    await this.command('Page.enable');
    await this.command('Runtime.enable');
    await this.command('Emulation.setDeviceMetricsOverride', {
      width: this.width, height: this.height, deviceScaleFactor: 1, mobile: false,
    });
    return this;
  }

  onData(chunk) {
    this.buffer += chunk.toString('utf8');
    let index;
    while ((index = this.buffer.indexOf('\0')) >= 0) {
      const raw = this.buffer.slice(0, index);
      this.buffer = this.buffer.slice(index + 1);
      if (!raw.trim()) continue;
      let message;
      try { message = JSON.parse(raw); } catch { continue; }
      if (message.id && this.pending.has(message.id)) {
        this.pending.get(message.id)(message);
        this.pending.delete(message.id);
        continue;
      }
      if (message.method === 'Runtime.consoleAPICalled') {
        const text = (message.params?.args || []).map((arg) => arg.value ?? arg.description ?? '').join(' ');
        this.consoleLogs.push({ type: message.params?.type || 'log', text });
        if (this.consoleLogs.length > 200) this.consoleLogs.shift();
      } else if (message.method === 'Runtime.exceptionThrown') {
        const detail = message.params?.exceptionDetails;
        this.exceptions.push(detail?.exception?.description || detail?.text || 'exception');
        if (this.exceptions.length > 50) this.exceptions.shift();
      }
    }
  }

  async setViewport(width, height) {
    this.width = width;
    this.height = height;
    await this.command('Emulation.setDeviceMetricsOverride', {
      width, height, deviceScaleFactor: 1, mobile: false,
    });
    await this.sleep(200);
  }

  command(method, params = {}) {
    const id = ++this.seq;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`CDP timeout: ${method}`));
      }, 60000);
      this.pending.set(id, (message) => {
        clearTimeout(timer);
        if (message.error) reject(new Error(`${method}: ${message.error.message}`));
        else resolve(message.result);
      });
      this.child.stdio[3].write(JSON.stringify({ id, method, params, sessionId: this.sessionId }) + '\0');
    });
  }

  async goto(url, { waitMs = 1500 } = {}) {
    await this.command('Page.navigate', { url });
    await this.waitFor(`document.readyState === 'complete'`, 20000);
    await this.sleep(waitMs);
  }

  async evaluate(expression) {
    const result = await this.command('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true,
    });
    if (result.exceptionDetails) throw new Error(`evaluate failed: ${result.exceptionDetails.text}`);
    return result.result?.value;
  }

  async waitFor(expression, timeoutMs = 20000) {
    const start = Date.now();
    for (;;) {
      try {
        if (await this.evaluate(`Boolean(${expression})`)) return true;
      } catch { /* page may be navigating */ }
      if (Date.now() - start > timeoutMs) throw new Error(`waitFor timed out: ${expression}`);
      await this.sleep(200);
    }
  }

  async click(selector) {
    const box = await this.evaluate(`(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      if (!el) return null;
      el.scrollIntoView({ block: 'center' });
      const r = el.getBoundingClientRect();
      return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
    })()`);
    if (!box) throw new Error(`click target not found: ${selector}`);
    await this.command('Input.dispatchMouseEvent', { type: 'mouseMoved', x: box.x, y: box.y });
    await this.command('Input.dispatchMouseEvent', { type: 'mousePressed', x: box.x, y: box.y, button: 'left', clickCount: 1 });
    await this.command('Input.dispatchMouseEvent', { type: 'mouseReleased', x: box.x, y: box.y, button: 'left', clickCount: 1 });
    await this.sleep(250);
  }

  async fill(selector, value) {
    await this.evaluate(`(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      el.focus();
      el.value = ${JSON.stringify(value)};
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    })()`);
  }

  async select(selector, value) {
    await this.evaluate(`(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      el.value = ${JSON.stringify(value)};
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return el.value;
    })()`);
  }

  async text(selector) {
    return this.evaluate(`document.querySelector(${JSON.stringify(selector)})?.innerText ?? ''`);
  }

  async screenshot(file) {
    const result = await this.command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, Buffer.from(result.data, 'base64'));
    return file;
  }

  sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  async close() {
    this.closing = true;
    try { await this.send('Browser.close'); } catch { /* ignore */ }
    await this.sleep(300);
    if (!this.exited) this.child.kill();
  }
}
