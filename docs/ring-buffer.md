# 링 버퍼(Ring Buffer) 구현 설명

APM SDK의 이벤트 전송을 **동기 방식 → 링 버퍼 기반 비동기 방식**으로 바꾸면서 직접 구현한 자료구조에 대한 설명입니다.

## 배경

기존에는 `ApmEventSender.send()` 안에서 요청 스레드가 직접 APM 서버로 HTTP 요청을 보내고 응답을 기다렸습니다.
이 구조에서는 APM 서버가 느려지면 모니터링 대상 서비스의 응답도 같이 느려집니다.

→ 요청 스레드는 **버퍼에 넣기만 하고 바로 반환**하고, 별도 전송 스레드가 버퍼에서 꺼내 보내도록 변경했습니다.

## 전체 흐름

```
[요청 스레드 여러 개]                    [전송 스레드 1개: apm-event-sender]
ApmInterceptor.afterCompletion()
   └─ sender.send(...)
        └─ buffer.offer(이벤트) ──▶  RingBuffer  ──▶ drainTo(최대 100개)
           (넣기만 하고 바로 반환)                      └─ 하나씩 POST /api/events
```

## 관련 파일

| 파일 | 역할 |
|---|---|
| `apm-sdk/src/main/java/com/leekyungmi/apm/sdk/buffer/RingBuffer.java` | 링 버퍼 구현 |
| `apm-sdk/src/main/java/com/leekyungmi/apm/sdk/sender/ApmEventSender.java` | 버퍼에 넣기 + 백그라운드 전송 |
| `apm-sdk/src/main/java/com/leekyungmi/apm/sdk/config/ApmAutoConfig.java` | 앱 종료 시 `shutdown()` 호출 |
| `apm-sdk/src/test/java/com/leekyungmi/apm/sdk/buffer/RingBufferTest.java` | 단위 테스트 / 동시성 테스트 |

---

## 1. `RingBuffer.java`: 핵심 자료구조

### 필드

```java
private final Object[] buffer;   // 실제 데이터를 담는 고정 크기 배열
private final int mask;          // capacity - 1
private long head = 0;           // 다음에 꺼낼 번호
private long tail = 0;           // 다음에 넣을 번호
private long droppedCount = 0;   // 가득 차서 버린 개수
```

**head와 tail은 배열 위치가 아니라 "몇 번째 원소인지"를 나타내는 번호입니다.** 이 번호는 계속 증가하기만 하고 0으로 돌아가지 않습니다. 배열 위치는 쓸 때마다 `번호 & mask`로 계산합니다.

용량 4일 때 예시:

```
offer A,B,C   → head=0, tail=3   배열: [A][B][C][ ]   size = 3-0 = 3
poll ×2       → head=2, tail=3   배열: [ ][ ][C][ ]   size = 1
offer D,E,F   → head=2, tail=6   배열: [E][F][C][D]   size = 4 (가득 참)
                                       ↑ tail=4 → 4&3=0, tail=5 → 5&3=1 (배열 앞으로 돌아옴)
```

- 비었는지: `head == tail`
- 가득 찼는지: `tail - head == capacity`

head와 tail을 배열 위치(0~3)로 관리하면 빈 상태와 가득 찬 상태 모두 `head == tail`이 되어 구분할 수 없습니다.
흔한 해결책은 칸 하나를 비워두는 것인데, 번호 방식을 쓰면 공간을 낭비하지 않고 구분할 수 있습니다.

> **Q. long도 언젠가 넘치지 않나요?**
> 1초에 10억 개씩 넣어도 약 292년이 걸리므로 실질적으로 문제가 없습니다.

### 생성자와 `nextPowerOfTwo`

```java
int capacity = nextPowerOfTwo(requestedCapacity);  // 5 → 8, 3000 → 4096
this.mask = capacity - 1;                          // 8이면 mask = 7 = 0b0111
```

- `Integer.highestOneBit(n)`은 n 이하에서 가장 큰 2의 거듭제곱을 돌려줍니다(5 → 4). n 자체가 2의 거듭제곱이면 그대로 쓰고, 아니면 왼쪽으로 한 칸 밀어서 두 배로 만듭니다(4 → 8).
- **2의 거듭제곱을 쓰는 이유**: 용량이 8이면 `x % 8`은 `x`의 마지막 3비트와 같습니다. 그래서 `x & 0b0111`로 같은 결과를 얻을 수 있고, 나눗셈보다 비트 연산이 빠릅니다.

### `offer` (넣기)

```java
public synchronized boolean offer(T item) {
    if (tail - head == buffer.length) {   // 가득 참
        droppedCount++;
        return false;                     // 기다리지 않고 바로 버림
    }
    buffer[(int) (tail & mask)] = item;   // 배열 위치 계산 후 저장
    tail++;
    return true;
}
```

- **가득 찼을 때 기다리지 않는 게 핵심 설계입니다.** `ArrayBlockingQueue.put()`처럼 공간이 생길 때까지 기다리면, 사용자의 실제 요청이 모니터링 때문에 멈춥니다.
- 모니터링 데이터 일부를 잃는 것보다 서비스 요청이 지연되는 쪽이 더 큰 손해라고 판단했습니다. 대신 버린 개수를 `droppedCount`로 집계해서 얼마나 버렸는지는 알 수 있게 했습니다.
- `null`을 넣으면 예외를 던집니다. `poll()`이 null을 "비어 있음"이라는 뜻으로 돌려주기 때문에, null이 원소로 들어오면 두 경우를 구분할 수 없습니다.

### `poll` / `drainTo` (꺼내기)

```java
T item = (T) buffer[index];
buffer[index] = null;   // 참조를 끊어줌
head++;
```

- `null`로 비우지 않으면 이미 꺼낸 이벤트 객체를 배열이 계속 참조합니다. 그러면 GC가 회수하지 못하고, 최대 4096개의 객체가 메모리에 계속 남습니다.
- `drainTo`는 락을 한 번만 잡고 여러 개를 꺼냅니다. `poll()`을 100번 부르면 락을 100번 잡았다 놓아야 하지만, 이렇게 하면 한 번으로 끝납니다.

### `synchronized`를 붙인 이유

`tail++`는 한 줄이지만 실제로는 다음 세 단계로 실행됩니다.

```
1. tail 값을 읽는다   (5)
2. 1을 더한다         (6)
3. tail에 쓴다        (6)
```

스레드 A와 B가 동시에 1단계에서 둘 다 5를 읽으면, 둘 다 `buffer[5]`에 쓰고 tail은 6이 됩니다. **이벤트 하나가 덮어써져서 사라집니다.**

`synchronized`가 하는 일:
- **원자성**: 한 번에 한 스레드만 메서드에 들어가게 하므로, 확인 → 쓰기 → 증가가 중간에 끊기지 않습니다.
- **가시성**: 한 스레드가 쓴 값을 다른 스레드가 반드시 볼 수 있게 보장합니다. `size()`, `droppedCount()`에도 `synchronized`를 붙인 이유입니다.
- `capacity()`는 값이 바뀌지 않는 `final` 배열의 길이라서 필요 없습니다.

---

## 2. `ApmEventSender.java`: 버퍼를 쓰는 쪽

### 생성자

```java
this.worker = new Thread(this::runLoop, "apm-event-sender");
this.worker.setDaemon(true);
this.worker.start();
```

- 객체가 만들어질 때 전송 전용 스레드를 하나 시작합니다.
- **데몬 스레드로 만든 이유**: 일반 스레드는 하나라도 살아 있으면 JVM이 종료되지 않습니다. 모니터링 SDK 때문에 앱이 꺼지지 않으면 안 되므로 데몬으로 만들었습니다.
- 스레드 이름을 붙여두면 스레드 덤프를 볼 때 어떤 스레드인지 바로 알 수 있습니다.

### `send` (요청 스레드에서 실행)

이벤트 정보를 Map으로 만들어 `buffer.offer(body)`로 넣고 끝납니다. HTTP 요청이 없어졌습니다.

`occurredAt`을 여기서 기록하는 이유: 실제 전송이 나중에 일어나기 때문에, 전송 시점에 기록하면 발생 시각이 틀어집니다.

### `runLoop` (전송 스레드에서 실행)

```java
while (running) {
    if (buffer.drainTo(batch, BATCH_SIZE) == 0) {   // 꺼낼 게 없으면
        Thread.sleep(IDLE_SLEEP_MS);                // 100ms 쉬었다가 다시 확인
        continue;
    }
    sendBatch(batch);                               // 있으면 전송
}
while (buffer.drainTo(batch, BATCH_SIZE) > 0) {     // 종료 전에 남은 것 전송
    sendBatch(batch);
}
```

- 버퍼가 비어 있을 때 쉬지 않고 계속 확인하면 CPU 코어 하나를 100% 점유합니다. 그래서 100ms씩 쉬도록 했습니다. 대신 이벤트 전송이 최대 100ms 늦어질 수 있습니다.
- `running`에 `volatile`을 붙인 이유: 다른 스레드(`shutdown()`을 호출한 스레드)가 바꾼 값을 이 루프가 바로 보게 하기 위해서입니다. `volatile`이 없으면 JIT 최적화로 값 변경을 영영 보지 못할 수 있습니다.

### `shutdown`

```java
running = false;        // 루프 종료 신호
worker.interrupt();     // sleep 중이면 바로 깨움
worker.join(3000);      // 남은 이벤트 전송을 최대 3초 기다림
```

---

## 3. `ApmAutoConfig.java`

`DisposableBean`을 구현해서 Spring이 종료될 때 `destroy()` → `apmEventSender.shutdown()`이 호출되도록 했습니다.
이게 없으면 앱이 꺼질 때 버퍼에 남은 이벤트가 그대로 사라집니다.

---

## 4. 테스트 (`RingBufferTest.java`)

| 테스트 | 검증 내용 |
|---|---|
| capacity는_2의_거듭제곱으로_올림된다 | 5 → 8, 3000 → 4096 |
| 넣은_순서대로_꺼낸다 | 먼저 넣은 것이 먼저 나오는지(FIFO) |
| 가득_차면_버리고_dropped를_센다 | 가득 찼을 때 false를 반환하고 버린 개수를 세는지 |
| 인덱스가_한바퀴_돌아도_순서가_유지된다 | 용량 4에서 넣고 빼기를 1만 번 반복해도 순서가 맞는지 |
| drainTo는_최대_개수만큼만_꺼낸다 | 최대 개수 제한이 지켜지는지 |
| 여러_스레드가_동시에_넣어도_유실되지_않는다 | 8개 스레드 × 10만 건 = 정확히 80만 건 |
| 비교용_동기화_없는_버전은_유실이_발생한다 | synchronized를 뺀 버전의 유실 수 출력 |

- 마지막 테스트는 실패로 처리하지 않고 결과만 출력합니다. 경쟁 조건은 실행할 때마다 발생 여부가 달라서, 실패로 처리하면 어떤 때는 통과하고 어떤 때는 실패하는 불안정한 테스트가 되기 때문입니다.
- `CountDownLatch`는 스레드 8개를 최대한 동시에 출발시켜 경쟁 상황을 확실히 만들려고 썼습니다.

### 동시성 테스트 결과 (synchronized 없는 버전, 8스레드 × 10만 건)

| 실행 | 기대 | 실제 | 유실 |
|---|---|---|---|
| 1 | 800,000 | 653,326 | 146,674 |
| 2 | 800,000 | 373,622 | 426,378 |
| 3 | 800,000 | 519,082 | 280,918 |

`synchronized`를 적용한 버전은 80만 건이 정확히 유지되었습니다.

실행 방법:

```bash
./gradlew :apm-sdk:test
```

---

## 5. 한계와 개선 방향

- **락 경쟁**: 넣는 쪽과 꺼내는 쪽이 같은 락 하나를 씁니다. 스레드가 많아지면 서로 기다리는 시간이 늘어나므로, `AtomicLong` CAS를 이용한 lock-free 구조가 다음 단계입니다.
- **전송 지연**: 버퍼가 비어 있으면 전송 스레드가 100ms씩 쉬기 때문에 이벤트가 최대 100ms 늦게 전송될 수 있습니다.
- **실제로는 한 건씩 전송**: 서버 API가 이벤트를 한 건씩만 받기 때문에 100개씩 꺼낸 뒤에도 HTTP는 한 건씩 보냅니다. 서버에 여러 건을 한 번에 받는 API를 만들면 진짜 묶음 전송이 됩니다.
- **버린 개수를 보는 곳이 없음**: `droppedCount`는 집계만 하고 아직 어디에도 보고하지 않습니다.
- **성능 비교 미실시**: JMH로 `ArrayBlockingQueue`와 처리량을 비교하는 것은 아직 하지 않았습니다.

---

## 면접 예상 질문 정리

1. head/tail을 왜 배열 위치가 아니라 계속 증가하는 번호(`long`)로 뒀나?
2. 왜 용량을 2의 거듭제곱으로 올리나?
3. 꺼낸 칸에 `buffer[index] = null`을 넣는 이유는?
4. `tail++`에서 왜 데이터가 사라지나?
5. 가득 찼을 때 왜 기다리지 않고 버리나?
6. `occurredAt`은 언제 기록하나? 왜?
7. 전송 스레드를 왜 데몬 스레드로 만들었나?
8. `running`에 `volatile`이 필요한 이유는?
9. `synchronized` 대신 lock-free로 바꾼다면 어떻게 할 것인가?
