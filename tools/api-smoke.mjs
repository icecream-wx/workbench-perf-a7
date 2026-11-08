#!/usr/bin/env node
/**
 * Generic API smoke for the generated app.
 * Checks health + OpenAPI, and that the backend responds. Business-specific
 * checks should be added here (or in another tools/*.mjs) by the agent.
 */
const BACKEND = process.env.APP_BACKEND_URL || 'http://127.0.0.1:8080';
const FRONTEND = process.env.APP_FRONTEND_URL || 'http://127.0.0.1:5173';

async function get(path) {
  const response = await fetch(BACKEND + path, { headers: { Accept: 'application/json' } });
  const text = await response.text();
  return { status: response.status, text };
}

let failed = false;
try {
  const health = await get('/actuator/health');
  const healthJson = JSON.parse(health.text);
  console.log('health:', health.status, healthJson.status || healthJson);
  if (health.status !== 200 || healthJson.status !== 'UP') failed = true;

  const openapi = await get('/v3/api-docs');
  const spec = JSON.parse(openapi.text);
  const paths = Object.keys(spec.paths || {});
  console.log('openapi:', openapi.status, 'paths:', paths.length, paths.slice(0, 8).join(', '));
  if (openapi.status !== 200) failed = true;

  const root = await fetch(FRONTEND + '/');
  console.log('frontend:', root.status);
  if (root.status !== 200) failed = true;
} catch (error) {
  console.error('API smoke failed:', error.message);
  failed = true;
}

if (failed) process.exitCode = 1;
else console.log('API smoke: PASS');
