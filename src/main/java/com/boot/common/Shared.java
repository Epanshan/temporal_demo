package com.boot.common;

public interface Shared {

    String MONEY_TRANSFER_TASK_QUEUE = "MONEY_TRANSFER_TASK_QUEUE";
    String ALGORITHM_TASK_QUEUE = "ALGORITHM_PRODUCT_TASK_QUEUE";
    String ALGORITHM_CONSUMER_TASK_QUEUE = "ALGORITHM_CONSUMER_TASK_QUEUE";
    String DELIMITER = "_";
    /**
     * 算法心跳标识
     */
    String ALGORITHM1 = "algorithm1";
    String WORK_IDS_REDIS_KEY = "WORK_IDS_REDIS_KEY";
    String CONSUMER_WORK_IDS_REDIS_KEY = "CONSUMER_WORK_IDS_REDIS_KEY";
}
