package com.boot.temporal.workflow;

import com.boot.config.TemporalConfig;
import com.boot.temporal.activity.AlgorithmActivity;
import com.boot.temporal.po.WorkerStreamReq;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.convert.DurationStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class AlgorithmWorkflowImpl implements AlgorithmWorkflow {


    private AtomicInteger sendCount = new AtomicInteger();

    // 传输方法是工作流的入口点。
    // 活动方法的执行可以在此处或从其他活动方法中进行编排
    @Override
    public void run(WorkerStreamReq req, TemporalConfig config) {
        log.info("开始执行编排逻辑。");
        // RetryOptions 指定如何在活动失败时自动处理重试。
        RetryOptions retryoptions = RetryOptions.newBuilder()
                .setMaximumInterval(DurationStyle.detectAndParse(config.getMaximumInterval()))
                .setInitialInterval(DurationStyle.detectAndParse(config.getInitialInterval()))
                .setBackoffCoefficient(config.getBackoffCoefficient())
//                .setMaximumAttempts(config.getMaximumAttempts())
                .build();
        ActivityOptions defaultActivityOptions = ActivityOptions.newBuilder()
                // 超时选项指定如果进程花费太长时间，何时自动超时活动。
                .setStartToCloseTimeout(DurationStyle.detectAndParse(config.getStartToCloseTimeout()))
                .setHeartbeatTimeout(DurationStyle.detectAndParse(config.getHeartbeatTimeout()))
                // （可选）提供自定义的 RetryOptions。
                // 默认情况下临时重试失败，这只是一个示例。
                .setRetryOptions(retryoptions)
                // 一个工作流愿意等待活动完成的总时间(限制活动的总执行时间)，包括重试,默认无限制
//                .setScheduleToCloseTimeout(DurationStyle.detectAndParse(config.getScheduleToCloseTimeout()))
//                .setScheduleToStartTimeout(DurationStyle.detectAndParse(config.getScheduleToStartTimeout()))
                .build();

        AlgorithmActivity algorithmActivity = Workflow.newActivityStub(AlgorithmActivity.class, defaultActivityOptions);
        String sequence = req.getSequence(), memo = req.getMemo();
        List<Promise<String>> promiseList = new ArrayList<>();
        List<String> workIds = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String uuid = Workflow.randomUUID().toString();
            workIds.add(uuid);
            ChildWorkflowOptions workflowOptions =
                    ChildWorkflowOptions.newBuilder().setWorkflowId(uuid)
                            .build();
            HelloChildWorkFlow child = Workflow.newChildWorkflowStub(HelloChildWorkFlow.class, workflowOptions);
            Promise<String> function = Async.function(child::sendMessage, req);
            promiseList.add(function);
        }
        log.info("开始执行编排逻辑。sequence：{} memo：{}", sequence, memo);
        algorithmActivity.terminateHandle();
        algorithmActivity.terminate(workIds);
        log.info("<<<<<执行编排的所有业务逻辑完成>>>>>");
        Promise.allOf(promiseList).get();
    }


    @Override
    public void increaseCount() {
        sendCount.incrementAndGet();
        System.out.println("***********" + sendCount.get());
    }

    @Override
    public Integer queryCount() {
        return sendCount.get();
    }
}
