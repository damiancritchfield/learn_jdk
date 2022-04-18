package com.lscode;

import com.lscode.juc.model.SumForkJoinTask;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

public class ForkJoinTest {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    @Test
    public void sumTest() throws ExecutionException, InterruptedException {
        int[] n = {1,2,3,4,5,6,7,8,9,10};
        List<Integer> nums = new ArrayList<>();
        int forSum = 0;
        for(int a : n){
            nums.add(a);
            forSum += a;
        }

        SumForkJoinTask sumForkJoinTask = new SumForkJoinTask(nums, 3);
        ForkJoinPool forkJoinPool = new ForkJoinPool();

        Future<Integer> future = forkJoinPool.submit(sumForkJoinTask);
        int forkJoinSum = future.get();

        logger.debug("[sumTest] sum array with fork/join, forkJoinSum=[{}]", forkJoinSum);
        logger.debug("[sumTest] sum array with for, forSum=[{}]", forSum);

        assertEquals(forSum, forkJoinSum);
    }

    @Test
    public void processorTest(){
        // 对于支持超线程的CPU来说，单个物理处理器相当于拥有两个逻辑处理器，能够同时执行两个线程
        // availableProcessors实际上应该是cpu线程数，例如8核16线程的cpu中，availableProcessors=16
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        System.out.println("availableProcessors=" + availableProcessors);
    }

}
