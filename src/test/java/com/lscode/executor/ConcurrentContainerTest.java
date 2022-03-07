package com.lscode.executor;

import org.junit.Test;

import java.util.HashMap;
import java.util.UUID;

public class ConcurrentContainerTest {

    @Test
    public void testHashMap() throws InterruptedException {
        final HashMap<String, String> map = new HashMap<String, String>(2);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    int finalI = i;
                    Thread t1=new Thread(new Runnable() {
                        @Override
                        public void run() {
                            for (int j = 0; j < 100; j++) {
                                map.put(UUID.randomUUID().toString(), "");
                                System.out.println("end:"+ finalI + "_" + j);
                            }
                        }
                    }, "ftf" + i);
                    t1.start();
                }
            }
        }, "ftf");
        t.start();
        t.join();
        System.out.println("completed");
    }

}
