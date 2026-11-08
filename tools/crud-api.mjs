#!/usr/bin/env node
/**
 * CRUD API smoke for the employee module. Covers list/create/update/
 * validation/conflict/404 with re-runnable random employee numbers.
 */
const BACKEND = process.env.APP_BACKEND_URL || 'http://127.0.0.1:8080';

async function call(path, options = {}) {
  const response = await fetch(BACKEND + path, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {}),
    },
  });
  const text = await response.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* non-JSON */ }
  return { status: response.status, json, text };
}

let failed = false;
function check(label, ok, detail = '') {
  console.log(`${ok ? 'PASS' : 'FAIL'} ${label}${detail ? `: ${detail}` : ''}`);
  if (!ok) failed = true;
}

function hasFieldError(body, field) {
  return Array.isArray(body?.fieldErrors) && body.fieldErrors.some((item) => item.field === field);
}

const code = `SMOKE${Date.now()}`;
const name = '烟测员工';
const payload = {
  employeeNo: code,
  name,
  gender: 'UNKNOWN',
  phone: '13900139000',
  email: 'crud-smoke@example.com',
  department: '测试部',
  position: '测试工程师',
  hireDate: '2024-02-01',
  status: 'ACTIVE',
};
try {
  const health = await call('/actuator/health');
  check('health UP', health.status === 200 && health.json?.status === 'UP');
  const openapi = await call('/v3/api-docs');
  check('openapi', openapi.status === 200 && Boolean(openapi.json?.paths?.['/api/employees']));

  const list = await call('/api/employees?page=1&size=10');
  check('list', list.status === 200 && Array.isArray(list.json?.items) && typeof list.json?.total === 'number');

  const created = await call('/api/employees', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  check('create 201', created.status === 201 && Boolean(created.json?.id), created.text.slice(0, 160));
  const createdId = created.json?.id;

  const searched = await call(`/api/employees?keyword=${encodeURIComponent(code)}`);
  check('list contains created', searched.status === 200
    && searched.json?.items?.some((item) => item.employeeNo === code));

  const duplicate = await call('/api/employees', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  check('duplicate 409', duplicate.status === 409 && hasFieldError(duplicate.json, 'employeeNo'),
    duplicate.text.slice(0, 160));

  const missing = await call('/api/employees', {
    method: 'POST',
    body: JSON.stringify({ name: '缺工号' }),
  });
  check('validation 400', missing.status === 400 && hasFieldError(missing.json, 'employeeNo'),
    missing.text.slice(0, 160));

  const updated = await call(`/api/employees/${createdId}`, {
    method: 'PUT',
    body: JSON.stringify({ ...payload, name: `${name}改`, status: 'INACTIVE' }),
  });
  check('update', updated.status === 200
    && updated.json?.name === `${name}改`
    && updated.json?.status === 'INACTIVE',
  updated.text.slice(0, 160));

  const notFound = await call('/api/employees/99999999', {
    method: 'PUT',
    body: JSON.stringify({ ...payload, employeeNo: `${code}X` }),
  });
  check('update 404', notFound.status === 404 && notFound.json?.code === 'EMPLOYEE_NOT_FOUND',
    notFound.text.slice(0, 160));
} catch (error) {
  console.error('CRUD API smoke failed:', error.message);
  failed = true;
}
if (failed) process.exitCode = 1;
else console.log('CRUD API smoke: PASS');
