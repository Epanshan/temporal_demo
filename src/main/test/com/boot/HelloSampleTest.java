package com.boot;/*
 *  Copyright (c) 2020 Temporal Technologies, Inc. All Rights Reserved
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */


import com.boot.config.TemporalConfig;
import com.boot.temporal.po.WorkerStreamReq;
import com.boot.temporal.workflow.*;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerOptions;
import io.temporal.workflow.Async;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;
import java.util.UUID;

import static io.temporal.api.enums.v1.WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING;

@SpringBootTest(classes = HelloSampleTest.Configuration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
public class HelloSampleTest {

    @Autowired
    ConfigurableApplicationContext applicationContext;

    TestWorkflowEnvironment testWorkflowEnvironment;

    WorkflowClient workflowClient;

    WorkerOptions workerOptions;


    @BeforeEach
    void setUp() {
        startWorker();
        //
        // applicationContext.start();
    }

    private void startWorker() {
        testWorkflowEnvironment = TestWorkflowEnvironment.newInstance();
        workflowClient = testWorkflowEnvironment.getWorkflowClient();
        workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentWorkflowTaskExecutionSize(200)
                .setMaxConcurrentActivityExecutionSize(200)
                .build();
        Worker worker = testWorkflowEnvironment.newWorker("ALGORITHM_PRODUCT_TASK_QUEUE11", workerOptions);
        worker.registerWorkflowImplementationTypes(AlgorithmWorkflowImpl.class, HelloChildWorkFlowImpl.class);
        testWorkflowEnvironment.start();

        WorkflowOptions workflowOptions =
                WorkflowOptions.newBuilder()
                        .setWorkflowId("CronHelloSample111")
                        .setTaskQueue("ALGORITHM_PRODUCT_TASK_QUEUE11")
                        .setWorkflowIdReusePolicy(WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING)
                        .build();

        AlgorithmWorkflow workflow = workflowClient.newWorkflowStub(AlgorithmWorkflow.class, workflowOptions);
        WorkerStreamReq workerStreamReq = new WorkerStreamReq();
        workerStreamReq.setSequence(UUID.randomUUID().toString());
        workerStreamReq.setMemo("0");
        workerStreamReq.setType(1);
        workflow.run(workerStreamReq, new TemporalConfig("10", "10", 10
                , 10, "10", "10"));

    }


    @Test
    public void testHello() {
        startWorker();
        WorkflowExecution execution = workflowClient.newUntypedWorkflowStub("CronHelloSample111").getExecution();
        DescribeWorkflowExecutionRequest describeWorkflowExecutionRequest =
                DescribeWorkflowExecutionRequest.newBuilder()
                        .setExecution(execution)
                        .build();
        DescribeWorkflowExecutionResponse resp = workflowClient.getWorkflowServiceStubs().blockingStub()
                .describeWorkflowExecution(describeWorkflowExecutionRequest);
        WorkflowExecutionInfo workflowExecutionInfo = resp.getWorkflowExecutionInfo();
        System.out.println("*******************************************************");

        System.out.println(workflowExecutionInfo.getStatus().toString());
        System.out.println("*******************************************************");


    }


    @ComponentScan
    public static class Configuration {
    }

}
