package com.lscode.juc;

import java.util.concurrent.locks.StampedLock;

public class StampedLockReadTest {
    public static void main(String[] args) throws InterruptedException {

        StampedLock stampedLock = new StampedLock();

        final int[] data = {0};

        Thread thread1 = new Thread(){
            @Override
            public void run() {
                System.out.println("开始加写锁");
                long stamp = stampedLock.writeLock(); // 获取写锁
                try {
                    System.out.println("加写锁完成，stamp = " + stamp);
                    data[0]++;
                    System.out.println("修改数据完成，data[0] = " + data[0]);
                    Thread.sleep(1000 * 10);
                }catch(Exception e){
                    System.out.println("占有锁失败");
                    e.printStackTrace();
                } finally {
                    System.out.println("准备释放写锁，data[0] = " + data[0]);
                    stampedLock.unlockWrite(stamp); // 释放写锁
                    System.out.println("释放写锁完成，data[0] = " + data[0]);
                }
            }
        };

        Thread thread5 = new Thread(){
            @Override
            public void run() {
                int read = 0;
                // 如果已经获取写锁未释放，tryOptimisticRead获取的一定是0，如果未获取写锁，或者写锁已经释放，返回的是state高位，以及前8位的最高一位（代表是否加锁）
                long stamp = stampedLock.tryOptimisticRead(); // 获得一个乐观读锁

                read = data[0];
                System.out.println("读取数据，read = " + read);

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // 此验证分几种情况
                // 1.如果别的线程已经获取写锁且未释放，tryOptimisticRead获取的一定是0，此验证必然失败
                // 2.否则，验证stamp高位（state高位，以及前8位的最高一位）是否与state的高位相等
                // 3.在读数过程中，如果有人加写锁或者释放写锁，必然会影响高位（state高位，以及前8位的最高一位），即验证必然失败
                if (!stampedLock.validate(stamp)) { // 检查乐观读锁后是否有其他写锁发生
                    stamp = stampedLock.readLock(); // 获取一个悲观读锁
                    try {
                        read = data[0];
                        System.out.println("读取数据2，read = " + read);
                    } finally {
                        stampedLock.unlockRead(stamp); // 释放悲观读锁
                    }
                }
                System.out.println("读取数据完成，read = " + read);
            }
        };

        thread5.start();
        Thread.sleep(1000);
        thread1.start();
    }
}
