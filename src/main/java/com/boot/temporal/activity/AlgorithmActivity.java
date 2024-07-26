package com.boot.temporal.activity;

import com.boot.temporal.po.WorkerStreamReq;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface AlgorithmActivity {

    @ActivityMethod
    WorkerStreamReq algorithm1(WorkerStreamReq req);

    @ActivityMethod
    void print(WorkerStreamReq req);

    @ActivityMethod
    void terminate(List<String> workIds);

    @ActivityMethod
    void terminateHandle();


    @ActivityMethod
    void terminateConsumer(List<String> workIds);

    @ActivityMethod
    void terminateHandleConsumer();

    @ActivityMethod
    void consumer();

    @ActivityMethod
    boolean db(WorkerStreamReq req);

    @ActivityMethod
    String redis(WorkerStreamReq req);

    @ActivityMethod
    void send();

    @ActivityMethod
    void clearSerializationCache(String sequence, String memo);
}
