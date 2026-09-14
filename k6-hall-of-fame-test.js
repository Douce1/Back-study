import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    cache_stampede_stress: {
      executor: 'constant-vus',
      vus: 100,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], 
    http_req_duration: ['p(95)<50'], 
  },
};

export default function () {
  // Docker 컨테이너에서 Windows 호스트의 8080 포트로 접근
  const url = 'http://host.docker.internal:8080/api/v1/leaderboard/season/1/hall-of-fame?page=0&size=10';
  
  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.get(url, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'content has 10 items': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.content && body.data.content.length === 10;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.01);
}