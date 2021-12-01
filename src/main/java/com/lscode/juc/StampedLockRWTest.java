package com.lscode.juc;

import java.util.concurrent.locks.StampedLock;

public class StampedLockRWTest {
    public static void main(String[] args) throws InterruptedException {

        StampedLock stampedLock = new StampedLock();

        final int[] data = {0};

        Thread thread1 = new Thread(){
            @Override
            public void run() {
                System.out.println("开始加写锁");
                long stamp = stampedLock.writeLock(); // 获取写锁
                try {
                    System.out.println("加写锁完成，stamp = " + Long.toBinaryString(stamp));
                    data[0]++;
                    System.out.println("修改数据完成，data[0] = " + data[0]);
                    Thread.sleep(1000 * 5);
                }catch(Exception e){
                    System.out.println("占有锁失败");
                    e.printStackTrace();
                } finally {
                    System.out.println("准备释放写锁，data[0] = " + data[0]);
                    System.out.println("释放写锁，stamp = " + Long.toBinaryString(stamp));
                    stampedLock.unlockWrite(stamp); // 释放写锁
                    System.out.println("释放写锁完成，data[0] = " + data[0]);
                }
            }
        };

        Thread thread5 = new Thread(){
            @Override
            public void run() {
                int read = 0;
                long stamp = stampedLock.readLock(); // 获取一个悲观读锁
                System.out.println("获取读锁，stamp = " + Long.toBinaryString(stamp));
                try {
                    try {
                        Thread.sleep(1000 * 10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    read = data[0];
                    System.out.println("读取数据2，read = " + read);
                } finally {
                    System.out.println("释放读锁，stamp = " + Long.toBinaryString(stamp));
                    stampedLock.unlockRead(stamp); // 释放悲观读锁
                }
            }
        };

        thread1.start();
        Thread.sleep(1000);
        thread5.start();
    }
}
