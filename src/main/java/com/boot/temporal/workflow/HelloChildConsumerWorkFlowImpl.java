package com.boot.temporal.workflow;

import com.boot.temporal.activity.AlgorithmActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class HelloChildConsumerWorkFlowImpl implements HelloChildConsumerWorkFlow {

    private AlgorithmActivity activity = Workflow.newActivityStub(AlgorithmActivity.class, ActivityOptions.newBuilder().setHeartbeatTimeout(Duration.ofMinutes(2)).setStartToCloseTimeout(Duration.ofMinutes(2)).build());

    @Override
    public void consumer() {
        activity.consumer();

    }
}
