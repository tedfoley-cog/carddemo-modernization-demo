// k6 load profile for the online API: sign-on, account view (COACTVWC) and transaction list (COTRN00C).
// Targets from the target architecture NFRs: read p95 <= 200 ms, navigation < 2 s, 60 concurrent admin users.
// Run: k6 run -e API_URL=http://localhost:8080 -e USERS=60 -e DURATION=2m validation/nfr/online-api.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const API = __ENV.API_URL || 'http://localhost:8080';
const USERS = Number(__ENV.USERS || 60);
const DURATION = __ENV.DURATION || '2m';
const THINK = Number(__ENV.THINK_SECONDS || 1);
const JSON_HDR = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    online_users: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [{ duration: '20s', target: USERS }, { duration: DURATION, target: USERS }, { duration: '10s', target: 0 }],
    },
  },
  thresholds: {
    'http_req_duration{op:accountView}': ['p(95)<200'],
    'http_req_duration{op:transactionList}': ['p(95)<200'],
    'http_req_duration{op:signon}': ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const ACCOUNTS = Array.from({ length: 50 }, (_, i) => String(i + 1).padStart(11, '0'));

export function setup() {
  const res = http.post(`${API}/api/v1/auth/signon`, JSON.stringify({ userId: 'ADMIN001', password: 'PASSWORD' }),
    { headers: JSON_HDR });
  check(res, { 'setup signon 200': (r) => r.status === 200 });
}

export default function () {
  const user = __VU % 2 === 0 ? 'ADMIN001' : 'USER0001';
  const s = http.post(`${API}/api/v1/auth/signon`, JSON.stringify({ userId: user, password: 'PASSWORD' }),
    { headers: JSON_HDR, tags: { op: 'signon' } });
  check(s, { 'signon 200': (r) => r.status === 200 });
  const auth = { headers: { Authorization: `Bearer ${s.json('token')}` } };
  for (let i = 0; i < 5; i++) {
    const acct = ACCOUNTS[Math.floor(Math.random() * ACCOUNTS.length)];
    const v = http.get(`${API}/api/v1/accounts?accountId=${acct}`, { ...auth, tags: { op: 'accountView' } });
    check(v, { 'account view 200': (r) => r.status === 200 });
    sleep(THINK);
    const t = http.get(`${API}/api/v1/transactions?page=1`, { ...auth, tags: { op: 'transactionList' } });
    check(t, { 'transaction list 200': (r) => r.status === 200 });
    sleep(THINK);
  }
}

export function handleSummary(data) {
  const out = __ENV.SUMMARY_JSON || 'reports/nfr/k6-summary.json';
  return { [out]: JSON.stringify(data, null, 2), stdout: '' };
}
