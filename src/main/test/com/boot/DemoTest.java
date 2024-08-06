package com.boot;

import com.boot.config.TemporalConfig;
import com.boot.temporal.po.WorkerStreamReq;
import com.boot.temporal.workflow.AlgorithmWorkflow;
import com.boot.temporal.workflow.AlgorithmWorkflowImpl;
import com.boot.temporal.workflow.HelloChildWorkFlowImpl;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;

import static io.temporal.internal.logging.LoggerTag.WORKFLOW_ID;

public class DemoTest {


    @Rule
    public TestWorkflowRule testWorkflowRule =
            TestWorkflowRule.newBuilder().setWorkflowTypes(AlgorithmWorkflowImpl.class, HelloChildWorkFlowImpl.class).build();


   private void setUp() {
        // Get a workflow stub using the same task queue the worker uses.
        WorkflowOptions workflowOptions =
                WorkflowOptions.newBuilder()
                        .setTaskQueue(testWorkflowRule.getTaskQueue())
                        .setWorkflowId(WORKFLOW_ID + 1)
                        .build();

        AlgorithmWorkflow workflow =
                testWorkflowRule
                        .getWorkflowClient()
                        .newWorkflowStub(AlgorithmWorkflow.class, workflowOptions);
        WorkerStreamReq workerStreamReq = new WorkerStreamReq();
        workerStreamReq.setSequence(UUID.randomUUID().toString());
        workerStreamReq.setMemo("0");
        workerStreamReq.setType(1);
        TemporalConfig config = new TemporalConfig("10", "10", 10
                , 10, "10", "10");
        WorkflowClient.start(workflow::run, workerStreamReq, config);
    }


    @Test
    public void testStatus() throws Exception {
        setUp();
        WorkflowExecution execution = testWorkflowRule.getWorkflowClient().newUntypedWorkflowStub(WORKFLOW_ID + 1).getExecution();
        DescribeWorkflowExecutionRequest describeWorkflowExecutionRequest =
                DescribeWorkflowExecutionRequest.newBuilder()
                        .setNamespace(testWorkflowRule.getWorkflowClient().getOptions().getNamespace())
                        .setExecution(execution)
                        .build();
        DescribeWorkflowExecutionResponse resp = testWorkflowRule.getWorkflowClient().getWorkflowServiceStubs().blockingStub()
                .describeWorkflowExecution(describeWorkflowExecutionRequest);
        WorkflowExecutionInfo workflowExecutionInfo = resp.getWorkflowExecutionInfo();


        Thread.sleep(10 * 1000);
        System.out.println("*******************************************************");
        System.out.println(workflowExecutionInfo.getStatus().toString());
        System.out.println("*******************************************************");

    }


    @Test
    public void testCount() {

    }


}
