package com.lscode.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class MemberTest {

    public static void main(String[] args) {

        ExecutorService fixedService = Executors.newFixedThreadPool(5);

        String fixedThreadPoolName = "ftpn_";
        ExecutorService fixedFactoryService = Executors.newFixedThreadPool(5, new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, fixedThreadPoolName + index.getAndIncrement());
            }
        });

        fixedService.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println("[run] ThreadName=" + Thread.currentThread().getName());
            }
        });


    }

}
