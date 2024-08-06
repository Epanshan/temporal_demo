package com.boot.temporal.workflow;

import com.boot.temporal.activity.AlgorithmActivity;
import com.boot.temporal.po.WorkerStreamReq;
import io.temporal.activity.ActivityOptions;

import io.temporal.workflow.Workflow;

import java.time.Duration;

public class HelloChildWorkFlowImpl implements HelloChildWorkFlow {

    private AlgorithmActivity activity =
            Workflow.newActivityStub(
                    AlgorithmActivity.class,
                    ActivityOptions.newBuilder()
                            .setHeartbeatTimeout(Duration.ofMinutes(2))
                            .setStartToCloseTimeout(Duration.ofMinutes(2))
                            .build());

    @Override
    public String sendMessage(WorkerStreamReq req) {
        activity.send();

        return "";
    }
}
