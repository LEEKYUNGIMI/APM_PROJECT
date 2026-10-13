package com.leekyungmi.apm.sdk.buffer;

import java.util.List;

/**
 * 고정 크기 원형 큐.
 * - 요청 스레드(생산자)는 offer()로 넣고 바로 돌아간다. 가득 차면 기다리지 않고 버린다.
 * - 전송 스레드(소비자)는 drainTo()로 여러 개를 한 번에 꺼낸다.
 */
public class RingBuffer<T> {

    private final Object[] buffer;
    private final int mask;      // capacity - 1 (capacity가 2의 거듭제곱이므로 % 대신 & 사용 가능)

    // head/tail을 배열 인덱스가 아닌 "누적 카운터"로 둔다.
    // size = tail - head 로 계산되므로 비어있음(0)과 가득 참(capacity)을 구분할 수 있다.
    private long head = 0;       // 다음에 꺼낼 위치
    private long tail = 0;       // 다음에 넣을 위치
    private long droppedCount = 0;

    public RingBuffer(int requestedCapacity) {
        if (requestedCapacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + requestedCapacity);
        }
        int capacity = nextPowerOfTwo(requestedCapacity);
        this.buffer = new Object[capacity];
        this.mask = capacity - 1;
    }

    /** 가득 차 있으면 false를 반환하고 버린다. 호출한 스레드를 절대 블로킹하지 않는다. */
    public synchronized boolean offer(T item) {
        if (item == null) {
            throw new NullPointerException("item");
        }
        if (tail - head == buffer.length) {
            droppedCount++;
            return false;
        }
        buffer[(int) (tail & mask)] = item;
        tail++;
        return true;
    }

    @SuppressWarnings("unchecked")
    public synchronized T poll() {
        if (head == tail) {
            return null;
        }
        int index = (int) (head & mask);
        T item = (T) buffer[index];
        buffer[index] = null;    // 꺼낸 객체를 GC가 회수할 수 있도록 참조 해제
        head++;
        return item;
    }

    /** 최대 maxItems개를 꺼내 target에 담고, 꺼낸 개수를 반환한다. */
    @SuppressWarnings("unchecked")
    public synchronized int drainTo(List<? super T> target, int maxItems) {
        int count = 0;
        while (head != tail && count < maxItems) {
            int index = (int) (head & mask);
            target.add((T) buffer[index]);
            buffer[index] = null;
            head++;
            count++;
        }
        return count;
    }

    public synchronized int size() {
        return (int) (tail - head);
    }

    public int capacity() {
        return buffer.length;
    }

    public synchronized long droppedCount() {
        return droppedCount;
    }

    static int nextPowerOfTwo(int n) {
        if (n > (1 << 30)) {
            throw new IllegalArgumentException("capacity too large: " + n);
        }
        int highest = Integer.highestOneBit(n);
        return highest == n ? n : highest << 1;
    }
}
