import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const ENDPOINT = __ENV.ENDPOINT || 'latest-cached';
const PROVIDER = __ENV.PROVIDER || 'BINANCE';
const URL = `${BASE}/api/v1/rates/${ENDPOINT}?provider=${PROVIDER}`;
const PARAMS = { headers: { Authorization: `Bearer ${__ENV.TOKEN}` }, timeout: '120s' };

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '30s',
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const res = http.get(URL, PARAMS);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'returned rates': (r) => r.body.length > 2,
  });
}
