package com.lscode.juc;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ConcurrentLinkedQueueTest {

    public static void main(String[] args) {
        ConcurrentLinkedQueue ctq = new ConcurrentLinkedQueue();
        ctq.add(1);
        ctq.add(2);
        ctq.add(3);
        ctq.add(4);
        System.out.println("end=" + ctq.poll());
        ctq.toString();
    }

}
