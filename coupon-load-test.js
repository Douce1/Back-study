import http from "k6/http";
import { check } from "k6";

// 100명의 가상 유저(VU)가 동시에 총 100회의 요청을 전송
export const options = {
  scenarios: {
    coupon_race_test: {
      executor: "per-vu-iterations",
      vus: 100,
      iterations: 1,
      maxDuration: "10s",
    },
  },
};

export default function () {
  // 테스트 대상 URL (실행 시 no-lock, db-lock, redis-lock으로 교체)
  const TARGET_TYPE = __ENV.TARGET || "redis-lock";
  const url = `http://localhost:8080/api/v1/coupons/1/issue/${TARGET_TYPE}`;

  const res = http.post(url);

  check(res, {
    "status is 200 (Success)": (r) => r.status === 200,
    "status is 400 (Sold Out)": (r) => r.status === 400,
  });
}
