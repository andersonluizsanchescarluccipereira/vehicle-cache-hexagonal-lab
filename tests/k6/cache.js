import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 5 },
    { duration: '20s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const base = __ENV.BASE_URL || 'http://localhost:8080/api/v1/veiculos';
const id = __ENV.VEHICLE_ID || '1';

export default function () {
  const response = http.get(`${base}/${id}`);
  check(response, {
    '200 OK': (r) => r.status === 200,
    'cache responde HIT ou MISS ou BYPASS': (r) => ['HIT', 'MISS', 'BYPASS'].includes(r.headers['X-Cache']),
  });
  sleep(0.1);
}
