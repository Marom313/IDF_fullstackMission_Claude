// k6 load test: eligibility check at 200 RPS for 60s. Target: p95 < 150ms, errors < 1%.
// Run: ipp perf   (k6 runs in a container on the compose network, against NGINX → gateway)
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://nginx';

export const options = {
  scenarios: {
    crossing: {
      executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 200), timeUnit: '1s',
      duration: __ENV.DURATION || '60s', preAllocatedVUs: 50, maxVUs: 200,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<150'],
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const r = http.post(`${BASE}/auth/realms/ipp/protocol/openid-connect/token`, {
    grant_type: 'password', client_id: 'ipp-web', username: 'officer1', password: 'Passw0rd!',
  });
  return { token: r.json('access_token') };
}

// 2,000 seeded ids (employed persons → mostly ALLOW, some expired/sanctioned)
const ids = [];
for (let i = 5; ids.length < 2000; i++) if (i % 10 < 6) ids.push('98' + String(i).padStart(7, '0'));

export default function (data) {
  const id = ids[Math.floor(Math.random() * ids.length)];
  const res = http.get(`${BASE}/api/eligibility/check?nationalId=${id}&purpose=ENTRY`, {
    headers: { Authorization: `Bearer ${data.token}` }, tags: { name: 'eligibility' },
  });
  check(res, { 'status 200': (r) => r.status === 200, 'has decision': (r) => !!r.json('decision') });
}
