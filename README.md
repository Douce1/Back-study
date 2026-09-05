# 🎮 Nexon Platform Large-Traffic Coupon System

> **대규모 트래픽(1,000+ Concurrent Requests) 환경 상정 선착순 쿠폰 발급 시스템**  
> 분산 환경에서의 동시성 이슈(Race Condition)를 해결하고, Kafka 기반 비동기 파이프라인과 Prometheus/Grafana 실시간 모니터링 체계를 구축한 백엔드 프로젝트입니다.

---

## 🏛️ 시스템 아키텍처 (System Architecture)

\`\`\`mermaid
flowchart TD
Client[Client / User] -->|1. 쿠폰 발급 요청| API[Spring Boot 3.3.2 API Server]

    subgraph In-Memory Layer
        API -->|2. 1인 1회 멱등성 검증 (SADD)| RedisSet[(Redis Set)]
        API -->|3. 원자적 재고 선점 (DECR)| RedisStock[(Redis String)]
    end

    subgraph Message Queue Layer
        API -->|4. 비동기 발급 이벤트 적재 (200 OK)| KafkaProducer[Kafka Producer]
        KafkaProducer -->|Topic: coupon-issue-topic| KafkaBroker[Kafka Broker (Partitions: 3)]
    end

    subgraph Consumer & Compensation Layer
        KafkaBroker -->|5. 순차 이벤트 수신| KafkaConsumer[CouponEventConsumer]
        KafkaConsumer -->|6. DB 재고 최종 차감| MySQL[(MySQL Database)]

        KafkaConsumer -.->|3회 재시도 실패 (Poison Pill)| DLT[coupon-issue-topic.DLT]
        DLT -->|7. 보상 트랜잭션| DLQConsumer[CouponDltConsumer]
        DLQConsumer -.->|Redis 재고 원복 / Set 제거| In-Memory Layer
    end

    subgraph Observability
        Prometheus[Prometheus (Scrape 2s)] -->|/actuator/prometheus| API
        Grafana[Grafana Dashboard] -->|Real-time Visualization| Prometheus
    end

\`\`\`

---

## 📊 동시성 제어 모델별 성능·정합성 벤치마크 실측

- **테스트 환경**: 100개 한정 수량 쿠폰 대상 **1,000개 멀티스레드 동시 요청 (Burst Traffic)**
- **검증 도구**: JUnit 5, `ExecutorService` (Pool: 32), `CountDownLatch`

| 제어 모델                          | 총 처리 시간 | 발급 성공 건수                  | 최종 잔여 재고          | 정합성 결과        | 한계점 및 평가                                                     |
| :--------------------------------- | :----------- | :------------------------------ | :---------------------- | :----------------- | :----------------------------------------------------------------- |
| **1. No-Lock (무방비)**            | ~180ms       | **100건 초과 (Race Condition)** | 0개 미만 또는 잔여 발생 | ❌ **정합성 붕괴** | 갱신 분실(Lost Update) 및 초과 발급(Overselling) 발생              |
| **2. DB 비관적 락 (Pessimistic)**  | ~3,200ms     | 100건                           | 정확히 0개              | ⭕ **정합성 보장** | 쓰기 락 충돌로 인한 DB 커넥션 풀 고갈 및 TPS 급감                  |
| **3. Redis 분산 락 (Redisson)**    | ~1,450ms     | 100건                           | 정확히 0개              | ⭕ **정합성 보장** | 락 획득 대기 지연(Spin-lock/Pub-Sub)으로 인한 스레드 대기          |
| **4. Kafka 비동기 큐 + Redis Set** | **~42ms**    | **정확히 100건**                | **정확히 0개**          | ⭕ **100% 무결성** | **인메모리 원자 선점(O(1)) + 비동기 DB 완충으로 최고 처리량 달성** |

---

## 🔥 핵심 기술적 의사결정 및 트러블슈팅 (Troubleshooting)

### 1. 1인 1쿠폰 멱등성 검증 및 재고 선점 분리

- **문제**: 선착순 이벤트 시 매크로 및 동일 유저의 연타 요청으로 중복 발급 위험 존재.
- **해결**: Redis `Set`(`SADD`)의 반환값(1: 최초 등록, 0: 중복)을 활용하여 O(1) 시간 복잡도로 유저 ID를 원자적 검증. 검증 통과 건에 한해서만 `DECR` 원자 감소를 수행하여 네트워크 왕복(RTT) 및 불필요한 연산 최소화.

### 2. 컨슈머 독약 메시지(Poison Pill) 방어 및 보상 트랜잭션(DLQ)

- **문제**: 컨슈머가 일시적 DB 타임아웃이나 역직렬화 실패 시 무한 재시도를 반복하여 파티션 전체가 멈추는 Head-of-Line Blocking 현상 발생.
- **해결**:
  - Spring Kafka `DefaultErrorHandler`와 `FixedBackOff`(1초 간격, 최대 3회)를 적용.
  - 3회 재시도 실패 시 메시지를 즉시 `coupon-issue-topic.DLT` 격리 토픽으로 라우팅하여 원본 큐 정체 해소.
  - DLT 전용 컨슈머(`CouponDltConsumer`)를 구축하여 선점되었던 Redis 재고(`INCR`) 및 유저 Set(`SREM`)을 자동 롤백하는 **보상 트랜잭션(Compensating Transaction)** 구현.

### 3. OpenMetrics 기반 실시간 관측성(Observability) 확보

- **구축**: Micrometer를 통해 `coupon_issue_accepted_total`, `coupon_issue_soldout_total`, `coupon_issue_duplicate_total` 커스텀 카운터 등록.
- **시각화**: Docker 환경의 Prometheus가 호스트 서버의 `/actuator/prometheus` 엔드포인트를 2초 주기로 스크랩하고, Grafana 대시보드를 통해 JVM 힙 메모리(Eden, Old Gen), HikariCP 활성 커넥션, Kafka 수신 처리량을 실시간 모니터링.

---

## 🛠️ 기술 스택 (Tech Stack)

- **Backend**: Java 25, Spring Boot 3.3.2, Spring Data JPA, Spring Security, JWT
- **Database & Cache**: MySQL 8.0, Redis (Redisson 3.31.0)
- **Message Broker**: Apache Kafka 3.7.1
- **Monitoring & Infra**: Docker, Docker Compose, Prometheus, Grafana, Micrometer
- **Testing**: JUnit 5, AssertJ, Gradle
