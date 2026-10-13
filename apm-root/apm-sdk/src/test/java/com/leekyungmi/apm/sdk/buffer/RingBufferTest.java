package com.leekyungmi.apm.sdk.buffer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class RingBufferTest {

    @Test
    void capacity는_2의_거듭제곱으로_올림된다() {
        assertEquals(1, new RingBuffer<>(1).capacity());
        assertEquals(8, new RingBuffer<>(5).capacity());
        assertEquals(8, new RingBuffer<>(8).capacity());
        assertEquals(4096, new RingBuffer<>(3000).capacity());
    }

    @Test
    void 넣은_순서대로_꺼낸다() {
        RingBuffer<Integer> rb = new RingBuffer<>(4);
        rb.offer(1);
        rb.offer(2);
        rb.offer(3);
        assertEquals(1, rb.poll());
        assertEquals(2, rb.poll());
        assertEquals(3, rb.poll());
        assertNull(rb.poll());
    }

    @Test
    void 가득_차면_버리고_dropped를_센다() {
        RingBuffer<Integer> rb = new RingBuffer<>(4);
        for (int i = 0; i < 4; i++) {
            assertTrue(rb.offer(i));
        }
        assertFalse(rb.offer(99));
        assertFalse(rb.offer(100));
        assertEquals(4, rb.size());
        assertEquals(2, rb.droppedCount());
    }

    @Test
    void 인덱스가_한바퀴_돌아도_순서가_유지된다() {
        RingBuffer<Integer> rb = new RingBuffer<>(4);
        // 넣고 빼기를 반복해 head/tail이 배열 끝을 여러 번 넘어가게 만든다
        for (int i = 0; i < 10_000; i++) {
            assertTrue(rb.offer(i));
            assertTrue(rb.offer(i + 1));
            assertEquals(i, rb.poll());
            assertEquals(i + 1, rb.poll());
        }
        assertEquals(0, rb.size());
    }

    @Test
    void drainTo는_최대_개수만큼만_꺼낸다() {
        RingBuffer<Integer> rb = new RingBuffer<>(8);
        for (int i = 0; i < 5; i++) {
            rb.offer(i);
        }
        List<Integer> out = new ArrayList<>();
        assertEquals(3, rb.drainTo(out, 3));
        assertEquals(List.of(0, 1, 2), out);
        assertEquals(2, rb.size());
    }

    @Test
    void 여러_스레드가_동시에_넣어도_유실되지_않는다() throws InterruptedException {
        int threads = 8;
        int perThread = 100_000;
        RingBuffer<Integer> rb = new RingBuffer<>(threads * perThread);

        runConcurrently(threads, () -> {
            for (int i = 0; i < perThread; i++) {
                rb.offer(i);
            }
        });

        assertEquals(threads * perThread, rb.size());
        assertEquals(0, rb.droppedCount());
    }

    /**
     * 비교용: synchronized를 빼면 어떻게 되는지 확인하는 테스트.
     * 경쟁 조건은 매번 발생한다는 보장이 없으므로 실패로 처리하지 않고 결과만 출력한다.
     */
    @Test
    void 비교용_동기화_없는_버전은_유실이_발생한다() throws InterruptedException {
        int threads = 8;
        int perThread = 100_000;
        NaiveRingBuffer rb = new NaiveRingBuffer(threads * perThread);

        runConcurrently(threads, () -> {
            for (int i = 0; i < perThread; i++) {
                rb.offer(i);
            }
        });

        int expected = threads * perThread;
        System.out.printf("[동기화 없음] 기대: %d, 실제 size: %d, 유실: %d%n",
                expected, rb.size(), expected - rb.size());
    }

    private static void runConcurrently(int threads, Runnable task) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> list = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread th = new Thread(() -> {
                try {
                    start.await();  // 모든 스레드가 최대한 동시에 출발하도록
                } catch (InterruptedException e) {
                    return;
                }
                task.run();
            });
            th.start();
            list.add(th);
        }
        start.countDown();
        for (Thread th : list) {
            th.join();
        }
    }

    /** RingBuffer에서 synchronized만 뺀 버전 (offer만 필요) */
    static class NaiveRingBuffer {
        private final Object[] buffer;
        private final int mask;
        private long head = 0;
        private long tail = 0;

        NaiveRingBuffer(int requestedCapacity) {
            int capacity = RingBuffer.nextPowerOfTwo(requestedCapacity);
            this.buffer = new Object[capacity];
            this.mask = capacity - 1;
        }

        boolean offer(Object item) {
            if (tail - head == buffer.length) {
                return false;
            }
            buffer[(int) (tail & mask)] = item;
            tail++;   // 읽기 → +1 → 쓰기 세 단계라 다른 스레드의 증가분을 덮어쓸 수 있다
            return true;
        }

        int size() {
            return (int) (tail - head);
        }
    }
}
