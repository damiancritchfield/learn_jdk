package com.lscode.executor;

import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static org.junit.Assert.assertTrue;

public class ExecutorDemoTest {

    /**
     * FixedThreadPool 适用于为了满足资源管理的需求，而需要限制当前线程数量的应用
     * 场景，它适用于负载比较重的服务器。
     * <p>任务不会按照提交顺序执行</p>
     */
    @Test
    public void newFixedThreadPoolTest(){
        ExecutorService fixedService = Executors.newFixedThreadPool(5);

        String fixedThreadPoolName = "ftpn_";
        ExecutorService fixedFactoryService = Executors.newFixedThreadPool(5, new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, fixedThreadPoolName + index.getAndIncrement());
            }
        });

        printTask(fixedService, 20);
        printTask(fixedFactoryService, 20);

        assertTrue( true );
    }

    /**
     * SingleThreadExecutor 适用于需要保证顺序地执行各个任务；并且在任意时间点，不会有多个线程是活动的应用场景。
     * 任务按照提交顺序执行
     */
    @Test
    public void newSingleThreadExecutorTest(){
        ExecutorService fixedService = Executors.newSingleThreadExecutor();

        String fixedThreadPoolName = "ftpn_";
        ExecutorService fixedFactoryService = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, fixedThreadPoolName + index.getAndIncrement());
            }
        });

        printTask(fixedService, 20);
        printTask(fixedFactoryService, 20);

        assertTrue( true );
    }

    @Test
    public void newCachedThreadPoolTest(){
        ExecutorService fixedService = Executors.newCachedThreadPool();

        String fixedThreadPoolName = "ftpn_";
        ExecutorService fixedFactoryService = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, fixedThreadPoolName + index.getAndIncrement());
            }
        });

        printTask(fixedService, 20);
        printTask(fixedFactoryService, 20);

        assertTrue( true );
    }

    @Test
    public void newScheduledThreadPoolTest() throws InterruptedException {
        ScheduledExecutorService fixedService = Executors.newScheduledThreadPool(5);
        ScheduledExecutorService fixedService2 = Executors.newSingleThreadScheduledExecutor();

        CountDownLatch countDownLatch = new CountDownLatch(20);
        fixedService.scheduleAtFixedRate(new Runnable() {
            AtomicInteger taskIndex = new AtomicInteger(0);
            @Override
            public void run() {
                System.out.println("[run] ThreadName=" + Thread.currentThread().getName() + ", taskIndex=" + taskIndex.getAndIncrement());
                countDownLatch.countDown();
            }
        }, 1000, 5000, TimeUnit.MILLISECONDS);

        fixedService.scheduleAtFixedRate(new Runnable() {
            AtomicInteger taskIndex = new AtomicInteger(0);
            @Override
            public void run() {
                System.out.println("[run] ThreadName=" + Thread.currentThread().getName() + ", taskIndex=" + taskIndex.getAndIncrement());
                countDownLatch.countDown();
            }
        }, 1000, 5000, TimeUnit.MILLISECONDS);

        AtomicInteger taskIndex = new AtomicInteger(0);
        fixedService2.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                System.out.println("[run] ThreadName=" + Thread.currentThread().getName() + ", taskIndex=" + taskIndex.getAndIncrement());
                countDownLatch.countDown();
            }
        }, 1000, 5000, TimeUnit.MILLISECONDS);

        fixedService2.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                System.out.println("[run] ThreadName=" + Thread.currentThread().getName() + ", taskIndex=" + taskIndex.getAndIncrement());
                countDownLatch.countDown();
            }
        }, 1000, 5000, TimeUnit.MILLISECONDS);

        countDownLatch.await();

        assertTrue( true );
    }

    private void printTask(ExecutorService executorService, int time){
        AtomicInteger taskIndex = new AtomicInteger(0);
        for(int i = 0; i < time; i++){
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println("[run] ThreadName=" + Thread.currentThread().getName() + ", taskIndex=" + taskIndex.getAndIncrement());
                }
            });
        }
    }

    @Test
    public void submitTest() throws ExecutionException, InterruptedException {
        ExecutorService fixedService = Executors.newFixedThreadPool(5);

        AtomicInteger callNum = new AtomicInteger(0);
        Future<String> call0 = fixedService.submit(new Callable<String>() {
            final String call = "call_" + callNum.getAndIncrement() + "_";
            @Override
            public String call() throws InterruptedException {
                Thread.sleep(5000);
                return call + System.currentTimeMillis();
            }
        });

        Future<String> call1 = fixedService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("call1 fun");
            }
        }, "call1");

        Future call2 =  fixedService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("call2 fun");
            }
        });

        System.out.println("call0 result=" + call0.get());
        System.out.println("call1 result=" + call1.get());
        System.out.println("call2 result=" + call2.get());

        assertTrue( true );
    }

    @Test
    public void futureTask(){
        ExecutorService fixedService = Executors.newFixedThreadPool(5);
        Future<String> future = fixedService.submit(new Callable<String>() {

            @Override
            public String call() throws Exception {
                System.out.println("start...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("end");
                return "completed";
            }
        });



        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("start cancel");
                try {
                    Thread.sleep(2000);
                } catch (Exception e){
                    System.out.println("sleep fail");
                }
                future.cancel(false);
            }
        }).start();

        String res = null;
        try {
            System.out.println("start get");
            res = future.get();
        } catch (Exception e) {
//            e.printStackTrace();
            System.out.println("get fail");
        }
        System.out.println("end ...res=" + res);
    }

    @Test
    public void futureTaskTest(){
        Callable<String> callable = new Callable<String>() {
            @Override
            public String call() throws Exception {
                System.out.println("start call");
                Thread.sleep(5000);
                System.out.println("end call");
                return "call123";
            }
        };
        FutureTask<String> futureTask = new FutureTask<>(callable);
        futureTask.run();
        try {
            System.out.println("start get");
            String res = futureTask.get();
            System.out.println(res);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void monitor() throws InterruptedException {
        ThreadPoolExecutor executorService = new ThreadPoolExecutor(1, 2,
                1000L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(2));

        printStatus(executorService);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                } catch (Exception e){
                    System.out.println("sleep fail");
                }
            }
        });

        printStatus(executorService);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (Exception e){
                    System.out.println("sleep fail");
                }
            }
        });

        printStatus(executorService);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (Exception e){
                    System.out.println("sleep fail");
                }
            }
        });

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (Exception e){
                    System.out.println("sleep fail");
                }
            }
        });

        printStatus(executorService);

        Thread.sleep(10000L);

        printStatus(executorService);
    }

    private void printStatus(ThreadPoolExecutor executorService){
        long taskCount = executorService.getTaskCount();
        long completedTaskCount = executorService.getCompletedTaskCount();
        int largestPoolSize = executorService.getLargestPoolSize();
        int poolSize = executorService.getPoolSize();
        int activeCount = executorService.getActiveCount();
        System.out.println("taskCount = " + taskCount
                + ", completedTaskCount = " + completedTaskCount
                + ", largestPoolSize," + largestPoolSize
                + ", poolSize," + poolSize
                + ", activeCount," + activeCount
        );
    }

    @Test
    public void re(){
        AtomicReferenceFieldUpdater atomicReferenceFieldUpdater = new AtomicReferenceFieldUpdater() {
            @Override
            public boolean compareAndSet(Object obj, Object expect, Object update) {
                return false;
            }

            @Override
            public boolean weakCompareAndSet(Object obj, Object expect, Object update) {
                return false;
            }

            @Override
            public void set(Object obj, Object newValue) {

            }

            @Override
            public void lazySet(Object obj, Object newValue) {

            }

            @Override
            public Object get(Object obj) {
                return null;
            }
        };
    }
}
