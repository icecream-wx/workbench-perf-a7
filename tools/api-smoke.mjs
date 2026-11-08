#!/usr/bin/env node
/**
 * Generic API smoke for the generated app plus the employee business cases
 * required by the batch (create/list/update/validation/conflict).
 */
const BACKEND = process.env.APP_BACKEND_URL || 'http://127.0.0.1:8080';
const FRONTEND = process.env.APP_FRONTEND_URL || 'http://127.0.0.1:5173';

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

const employeeNo = `API${Date.now()}`;
const name = '接口烟测员工';
try {
  const health = await call('/actuator/health');
  check('health UP', health.status === 200 && health.json?.status === 'UP');

  const openapi = await call('/v3/api-docs');
  check('openapi', openapi.status === 200 && Boolean(openapi.json?.paths?.['/api/employees']));
  const employeePaths = openapi.json?.paths?.['/api/employees'] || {};
  check('openapi list/create operations',
    Boolean(employeePaths.get) && Boolean(employeePaths.post));

  const created = await call('/api/employees', {
    method: 'POST',
    body: JSON.stringify({
      employeeNo,
      name,
      gender: 'MALE',
      phone: '13800138000',
      email: 'smoke@example.com',
      department: '研发部',
      position: '工程师',
      hireDate: '2024-01-31',
      status: 'ACTIVE',
    }),
  });
  check('create employee 201',
    created.status === 201 && created.json?.employeeNo === employeeNo && Boolean(created.json?.id),
    created.text.slice(0, 160));

  const listed = await call(`/api/employees?page=1&size=10&keyword=${encodeURIComponent(employeeNo)}`);
  check('list contains created employee',
    listed.status === 200
      && listed.json?.total >= 1
      && listed.json?.items?.some((item) => item.employeeNo === employeeNo)
      && listed.json?.page === 1
      && listed.json?.size === 10,
    listed.text.slice(0, 160));

  const updated = await call(`/api/employees/${created.json?.id}`, {
    method: 'PUT',
    body: JSON.stringify({
      employeeNo,
      name: `${name}改`,
      gender: 'FEMALE',
      phone: '13800138000',
      email: 'smoke@example.com',
      department: '平台部',
      position: '高级工程师',
      hireDate: '2024-01-31',
      status: 'INACTIVE',
    }),
  });
  check('update employee', updated.status === 200
    && updated.json?.name === `${name}改`
    && updated.json?.department === '平台部'
    && updated.json?.status === 'INACTIVE',
  updated.text.slice(0, 160));

  const missing = await call('/api/employees', {
    method: 'POST',
    body: JSON.stringify({ name: '缺工号' }),
  });
  check('missing employeeNo 400',
    missing.status === 400 && hasFieldError(missing.json, 'employeeNo'),
    missing.text.slice(0, 160));

  const duplicate = await call('/api/employees', {
    method: 'POST',
    body: JSON.stringify({ employeeNo, name: '重复工号', status: 'ACTIVE' }),
  });
  check('duplicate employeeNo 409',
    duplicate.status === 409 && hasFieldError(duplicate.json, 'employeeNo'),
    duplicate.text.slice(0, 160));

  const invalidStatus = await call('/api/employees?status=UNKNOWN');
  check('invalid status 400', invalidStatus.status === 400, invalidStatus.text.slice(0, 160));

  const oversized = await call('/api/employees?size=101');
  check('size 101 returns 400', oversized.status === 400, oversized.text.slice(0, 160));

  const root = await fetch(FRONTEND + '/');
  check('frontend reachable', root.status === 200);
} catch (error) {
  console.error('API smoke failed:', error.message);
  failed = true;
}

if (failed) process.exitCode = 1;
else console.log('API smoke: PASS');
