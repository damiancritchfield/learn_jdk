package com.lscode.juc.model;

import com.lscode.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.RecursiveTask;

public class SumForkJoinTask extends RecursiveTask<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(SumForkJoinTask.class);

    private final List<Integer> nums;
    private final int taskSize;

    public SumForkJoinTask(List<Integer> nums, int taskSize) {
        this.nums = nums;
        this.taskSize = taskSize;
    }

    @Override
    protected Integer compute() {

        if(nums == null || nums.size() == 0){
            return 0;
        }
        if(taskSize <= 0 || nums.size() <= taskSize){
            return sum(nums);
        }

        int mid = nums.size() / 2;

        List<Integer> leftList = nums.subList(0, mid);
        List<Integer> rightList = nums.subList(mid, nums.size());
        SumForkJoinTask leftForkJoinTask = new SumForkJoinTask(leftList, taskSize);
        SumForkJoinTask rightForkJoinTask = new SumForkJoinTask(rightList, taskSize);

        leftForkJoinTask.fork();
        rightForkJoinTask.fork();

        int left = leftForkJoinTask.join();
        int right = rightForkJoinTask.join();

        return left + right;
    }

    private int sum(List<Integer> numbers){
        if(numbers == null || numbers.size() == 0){
            return 0;
        }

        StringBuilder numStr = new StringBuilder();
        int sum = 0;
        for (Integer n : numbers) {
            sum += n;
            numStr.append(n).append(",");
        }
        logger.debug("[sum] sum=[{}], numbers=[{}]", sum, numStr);
        return sum;
    }
}
