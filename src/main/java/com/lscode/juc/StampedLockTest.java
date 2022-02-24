package com.lscode.juc;

import java.util.concurrent.locks.StampedLock;

public class StampedLockTest {
    public static void main(String[] args) throws InterruptedException {
        StampedLockPoint stampedLockPoint = new StampedLockPoint();
        stampedLockPoint.move(1,2);

        StampedLock stampedLock = new StampedLock();

        Thread thread1 = new Thread(){
            @Override
            public void run() {
                System.out.println("开始加锁写锁，并占有30分钟");
                long stamp = stampedLock.writeLock(); // 获取写锁
                try {
                    System.out.println("stamp = " + stamp);
                    Thread.sleep(1000 * 3600);
                }catch(Exception e){
                    System.out.println("占有锁失败");
                    e.printStackTrace();
                } finally {
                    stampedLock.unlockWrite(stamp); // 释放写锁
                }
            }
        };

        Thread thread2 = new Thread(){
            @Override
            public void run() {
                System.out.println("开始加锁写锁，并占有30分钟");
                long stamp = stampedLock.writeLock(); // 获取写锁
                try {
                    System.out.println("stamp = " + stamp);
                    Thread.sleep(1000 * 3600 * 24);
                }catch(Exception e){
                    System.out.println("占有锁失败");
                    e.printStackTrace();
                } finally {
                    stampedLock.unlockWrite(stamp); // 释放写锁
                }
            }
        };

        Thread thread3 = new Thread(){
            @Override
            public void run() {
                System.out.println("开始加锁写锁，并占有30分钟");
                long stamp = stampedLock.writeLock(); // 获取写锁
                try {
                    System.out.println("stamp = " + stamp);
                    Thread.sleep(1000 * 3600 * 24);
                }catch(Exception e){
                    System.out.println("占有锁失败");
                    e.printStackTrace();
                } finally {
                    stampedLock.unlockWrite(stamp); // 释放写锁
                }
            }
        };

        Thread thread4 = new Thread(){
            @Override
            public void run() {
                System.out.println("开始加锁读锁，并占有30分钟");
                long stamp = stampedLock.readLock(); // 获取写锁
                try {
                    System.out.println("stamp = " + stamp);
                    Thread.sleep(1000 * 3600 * 24);
                }catch(Exception e){
                    System.out.println("占有锁失败");
                    e.printStackTrace();
                } finally {
                    stampedLock.unlockRead(stamp); // 释放写锁
                }
            }
        };

        Thread thread5 = new Thread(){
            @Override
            public void run() {
                // 如果已经获取写锁未释放，tryOptimisticRead获取的一定是0，如果未获取写锁，或者写锁已经释放，返回的是state高位
                long stamp = stampedLock.tryOptimisticRead(); // 获得一个乐观读锁
                // 注意下面两行代码不是原子操作
                // 假设x,y = (100,200)
                double currentX = 1;
                // 此处已读取到x=100，但x,y可能被写线程修改为(300,400)
                double currentY = 2;
                // 此处已读取到y，如果没有写入，读取是正确的(100,200)
                // 如果有写入，读取是错误的(100,400)

                // 如果锁已经获取写锁未释放，此验证一定失败
                // 否则，验证stamp高位是否与state的高位相等
                // 在读数过程中，如果有人加写锁，不会影响高位，即验证能通过
                // 如果有人释放写锁，会影响高位+1，验证会失败
                if (!stampedLock.validate(stamp)) { // 检查乐观读锁后是否有其他写锁发生
                    stamp = stampedLock.readLock(); // 获取一个悲观读锁
                    try {
                        currentX = 1;
                        currentY = 2;
                    } finally {
                        stampedLock.unlockRead(stamp); // 释放悲观读锁
                    }
                }
                System.out.println("x=" + currentX + ", y=" + currentY);
            }
        };

        thread1.start();
        Thread.sleep(1000);
//        thread2.start();
//        Thread.sleep(1000);
//        thread3.start();
//        Thread.sleep(1000);
//        thread4.start();
        thread5.start();
    }
}
