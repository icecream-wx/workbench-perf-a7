#!/usr/bin/env node
/**
 * Instantiate the generic CRUD sample for one entity.
 *
 *   node scripts/use-crud-sample.mjs --entity Employee --plural employees \
 *     --code employeeNo --date hireDate --label 员工 --code-label 工号 --date-label 入职日期 --table employee
 *
 * It copies backend domain/repository/service/controller/DTO/migration/test,
 * frontend api/view, and CRUD smoke helpers. The agent must still adapt the
 * business fields/rules and run the one-shot verify.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const SAMPLE = path.join(ROOT, 'samples', 'crud');
const args = process.argv.slice(2);
const opts = {};
for (let i = 0; i < args.length; i += 1) {
  if (args[i].startsWith('--')) {
    const key = args[i].slice(2);
    const value = args[i + 1] && !args[i + 1].startsWith('--') ? args[++i] : 'true';
    opts[key] = value;
  }
}
const entity = opts.entity;
if (!entity || !/^[A-Z][A-Za-z0-9]*$/.test(entity)) {
  console.error('usage: node scripts/use-crud-sample.mjs --entity Employee [--plural employees] [--code employeeNo] [--date hireDate] [--label 员工] [--package com.example.demo] [--force]');
  process.exit(2);
}
const plural = opts.plural || `${entity.toLowerCase()}s`;
const code = opts.code || 'code';
const date = opts.date || 'date';
const label = opts.label || entity;
const codeLabel = opts['code-label'] || code;
const dateLabel = opts['date-label'] || date;
const table = opts.table || plural;
const force = opts.force === 'true';

function findPackage() {
  if (opts.package) return opts.package;
  const base = path.join(ROOT, 'backend', 'src', 'main', 'java');
  const stack = [base];
  while (stack.length) {
    const dir = stack.pop();
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) stack.push(full);
      else if (entry.name === 'Application.java') {
        return path.relative(base, dir).split(path.sep).join('.');
      }
    }
  }
  throw new Error('cannot detect package; pass --package');
}
const pkg = findPackage();
const pkgPath = pkg.split('.').join(path.sep);
const pascal = (value) => value.charAt(0).toUpperCase() + value.slice(1);
const snake = (value) => value.replace(/[A-Z]/g, (m) => `_${m.toLowerCase()}`);

const map = {
  __PACKAGE__: pkg,
  Member: entity,
  members: plural,
  memberNo: code,
  MemberNo: pascal(code),
  member_no: snake(code),
  joinDate: date,
  JoinDate: pascal(date),
  join_date: snake(date),
  '成员编号': codeLabel,
  '加入日期': dateLabel,
  member: entity.toLowerCase(),
  '成员': label,
};
const keys = Object.keys(map).sort((a, b) => b.length - a.length);
const pattern = new RegExp(keys.map((key) => key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|'), 'g');
const convert = (text) => text.replace(pattern, (match) => map[match]);

function copy(sourceRelative, targetRelative) {
  const source = path.join(SAMPLE, sourceRelative);
  const target = path.join(ROOT, convert(targetRelative));
  if (!fs.existsSync(source)) throw new Error(`sample missing: ${sourceRelative}`);
  if (fs.existsSync(target) && !force) throw new Error(`target exists (use --force): ${target}`);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, convert(fs.readFileSync(source, 'utf8')), 'utf8');
  console.log('wrote', path.relative(ROOT, target));
}

if (!fs.existsSync(SAMPLE)) {
  console.error('samples/crud 不存在；请使用最新脚手架生成应用');
  process.exit(1);
}

const javaBase = path.join('backend', 'src', 'main', 'java', pkgPath);
const testBase = path.join('backend', 'src', 'test', 'java', pkgPath);
for (const [source, target] of [
  ['backend/java/domain/Member.java', path.join(javaBase, 'domain', `${entity}.java`)],
  ['backend/java/domain/Gender.java', path.join(javaBase, 'domain', 'Gender.java')],
  ['backend/java/repository/MemberRepository.java', path.join(javaBase, 'repository', `${entity}Repository.java`)],
  ['backend/java/service/MemberService.java', path.join(javaBase, 'service', `${entity}Service.java`)],
  ['backend/java/controller/MemberController.java', path.join(javaBase, 'controller', `${entity}Controller.java`)],
  ['backend/java/controller/MemberRequest.java', path.join(javaBase, 'controller', `${entity}Request.java`)],
  ['backend/java/controller/MemberResponse.java', path.join(javaBase, 'controller', `${entity}Response.java`)],
  ['backend/java/controller/MemberPageResponse.java', path.join(javaBase, 'controller', `${entity}PageResponse.java`)],
  ['backend/test/MemberApiTests.java', path.join(testBase, `${entity}ApiTests.java`)],
  ['backend/migration/V2__member.sql', path.join('backend', 'src', 'main', 'resources', 'db', 'migration', `V2__${table}.sql`)],
  ['frontend/api/members.ts', path.join('frontend', 'src', 'api', `${plural}.ts`)],
  ['frontend/views/MemberListView.vue', path.join('frontend', 'src', 'views', `${entity}ListView.vue`)],
  ['tools/crud-api.mjs', path.join('tools', 'crud-api.mjs')],
  ['tools/crud-ui.mjs', path.join('tools', 'crud-ui.mjs')],
]) copy(source, target);

const routerFile = path.join(ROOT, 'frontend', 'src', 'router', 'index.ts');
if (fs.existsSync(routerFile)) {
  const route = `    { path: '/${plural}', name: '${plural}', component: () => import('../views/${entity}ListView.vue') },\n`;
  const text = fs.readFileSync(routerFile, 'utf8');
  if (!text.includes(`path: '/${plural}'`)) {
    const marker = 'routes: [';
    const index = text.indexOf(marker);
    if (index >= 0) {
      const next = text.slice(0, index + marker.length) + '\n' + route + text.slice(index + marker.length);
      fs.writeFileSync(routerFile, next, 'utf8');
      console.log('router: added', `/${plural}`);
    }
  }
}

console.log('\nCRUD sample copied. Next:');
console.log('- adapt fields/validation/messages to the batch spec; keep tests (add business cases if needed)');
console.log('- run: node scripts/verify.mjs (once before commit)');
console.log('- commit/push code + .opencode/delivery/<batch>.md');
