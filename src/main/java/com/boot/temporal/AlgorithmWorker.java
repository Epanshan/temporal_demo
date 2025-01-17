package com.boot.temporal;

import com.boot.common.Shared;
import com.boot.config.TemporalConfig;
import com.boot.temporal.activity.AlgorithmActivityImpl;
import com.boot.temporal.po.WorkerStreamReq;
import com.boot.temporal.workflow.*;
import io.grpc.StatusRuntimeException;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.temporal.api.enums.v1.WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING;

/**
 * 功能描述: 算法工人
 *
 * @author No.007
 * @date 2021/11/19 0019 15:05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlgorithmWorker implements Shared {

    @Value("${configuration.temporal.target}")
    private String target;
    @Value("${configuration.temporal.enabledWorkflow:true}")
    private Boolean enabledWorkflow;
    @Value("${configuration.temporal.maxConcurrentWorkflowTaskExecutionSize:200}")
    private Integer maxConcurrentWorkflowTaskExecutionSize;
    @Value("${configuration.temporal.maxConcurrentActivityExecutionSize:200}")
    private Integer maxConcurrentActivityExecutionSize;

    @Value("${configuration.temporal.retry.initialInterval:1}")
    private String initialInterval;
    @Value("${configuration.temporal.retry.maximumInterval:60s}")
    private String maximumInterval;
    @Value("${configuration.temporal.retry.backoffCoefficient:2}")
    private Integer backoffCoefficient;
    @Value("${configuration.temporal.retry.maximumAttempts:99999999}")
    private Integer maximumAttempts;
    @Value("${configuration.temporal.activity.startToCloseTimeout:3d}")
    private String startToCloseTimeout;

    @Value("${configuration.temporal.activity.heartbeatTimeout:600s}")
    private String heartbeatTimeout;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Resource
    private RedisTemplate redisTemplate;


    /**
     * 工作流客户端
     */
    private static WorkflowClient client;
    /**
     * Worker 配置选项
     */
    private static WorkerOptions workerOptions;

    //@PostConstruct
    public void init() {

        WorkflowServiceStubsOptions workflowServiceStubsOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .setHealthCheckTimeout(Duration.ofSeconds(100))
                .setRpcTimeout(Duration.ofSeconds(10000))
                .setKeepAliveTime(Duration.ofSeconds(100))
                .setHealthCheckAttemptTimeout(Duration.ofSeconds(10000))
                .build();

        WorkflowServiceStubs service = WorkflowServiceStubs.newInstance(workflowServiceStubsOptions);
        client = WorkflowClient.newInstance(service);

        workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentWorkflowTaskExecutionSize(maxConcurrentWorkflowTaskExecutionSize)
                .setMaxConcurrentActivityExecutionSize(maxConcurrentActivityExecutionSize)
                .build();

        if (enabledWorkflow) {
            startWorker();
        }
        Boolean master = redisTemplate.opsForValue().setIfAbsent("work-master", "master", 30, TimeUnit.SECONDS);
        log.info("master work 竞争结果**********, result is:{}", master);
        if (master != null && master) {
            log.info("role is ********* master work,master ");
            initCronWorkflow();
        } else {
            log.info("role is ********* work");
        }
    }


    public static WorkflowClient client() {
        return client;
    }


    /**
     * 功能描述: 启动算法工作流引擎
     *
     * @return void
     * @author No.007
     * @date 2021/11/19 0019 14:26
     */
    private void startWorker() {
        // WorkflowServiceStubs 是一个 gRPC 存根包装器，它与临时服务器的本地 Docker 实例进行对话。
        // Worker factory 用于创建轮询特定任务队列的工人。
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(ALGORITHM_TASK_QUEUE, workerOptions);
        // 此 Worker 承载工作流和活动实现。工作流是有状态的，因此需要一种类型来创建实例。
        worker.registerWorkflowImplementationTypes(AlgorithmWorkflowImpl.class, HelloChildWorkFlowImpl.class);
        // 活动是无状态和线程安全的，因此使用共享实例。
        worker.registerActivitiesImplementations(new AlgorithmActivityImpl(redisTemplate));

        Worker consumerWorker = factory.newWorker(ALGORITHM_CONSUMER_TASK_QUEUE, workerOptions);
        consumerWorker.registerWorkflowImplementationTypes(AlgorithmConsumerWorkflowImpl.class, HelloChildConsumerWorkFlowImpl.class);
        // 活动是无状态和线程安全的，因此使用共享实例。
        consumerWorker.registerActivitiesImplementations(new AlgorithmActivityImpl(redisTemplate));
        // 开始侦听任务队列。
        factory.start();
        log.info("启动Worker成功，监听队列：{} 暂停状态：{}", worker.getTaskQueue(), worker.isSuspended());
    }

    private void initCronWorkflow() {

        WorkflowOptions workflowOptions =
                WorkflowOptions.newBuilder()
                        .setWorkflowId("CronHelloSample")
                        .setTaskQueue(ALGORITHM_TASK_QUEUE)
                        .setCronSchedule("0/1 * * * ?")
                        .setWorkflowIdReusePolicy(WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING)
                        .build();

        AlgorithmWorkflow workflow = client.newWorkflowStub(AlgorithmWorkflow.class, workflowOptions);
        WorkerStreamReq workerStreamReq = new WorkerStreamReq();
        workerStreamReq.setSequence(UUID.randomUUID().toString());
        workerStreamReq.setMemo("0");
        workerStreamReq.setType(1);
       /* workflow.run(workerStreamReq, new TemporalConfig(initialInterval, maximumInterval, backoffCoefficient
                , maximumAttempts, startToCloseTimeout, heartbeatTimeout));*/

        new Thread(() -> {
            workflow.run(workerStreamReq, new TemporalConfig(initialInterval, maximumInterval, backoffCoefficient
                    , maximumAttempts, startToCloseTimeout, heartbeatTimeout));
        }).start();
        WorkflowOptions workflowConsumerOptions =
                WorkflowOptions.newBuilder()
                        .setWorkflowId("CronHelloSampleConsumer")
                        .setTaskQueue(ALGORITHM_CONSUMER_TASK_QUEUE)
                        .setCronSchedule("0/2 * * * ?")
                        .setWorkflowIdReusePolicy(WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING)
                        .build();

        AlgorithmConsumerWorkflow workflowConsumer = client.newWorkflowStub(AlgorithmConsumerWorkflow.class, workflowConsumerOptions);
        WorkerStreamReq consumer = new WorkerStreamReq();
        consumer.setSequence(UUID.randomUUID().toString());
        consumer.setMemo("0");
        consumer.setType(1);
        new Thread(() -> {
            workflowConsumer.run(consumer, new TemporalConfig(initialInterval, maximumInterval, backoffCoefficient
                    , maximumAttempts, startToCloseTimeout, heartbeatTimeout));
        }).start();
    /*    workflowConsumer.run(consumer, new TemporalConfig(initialInterval, maximumInterval, backoffCoefficient
                , maximumAttempts, startToCloseTimeout, heartbeatTimeout));*/
    }


    /**
     * 向工作流发送消息
     *
     * @param req
     */
    public void send(WorkerStreamReq req) {
        String sequence = req.getSequence(), memo = req.getMemo(), workflowId = req.getSequence();
        // WorkflowServiceStubs 是一个 gRPC 存根包装器，它与临时服务器的本地 Docker 实例进行对话。
        try {
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setTaskQueue(ALGORITHM_TASK_QUEUE)
                    // WorkflowId 防止它具有重复的实例，将其删除以进行复制。
                    .setWorkflowId(workflowId)
                    .setWorkflowTaskTimeout(Duration.ofHours(1))
//                    .setWorkflowExecutionTimeout(Duration.ofMinutes(3))
//                    .setWorkflowRunTimeout(Duration.ofMinutes(3))
                    .build();

            // WorkflowStubs 启用对方法的调用，就好像 Workflow 对象是本地的一样，但实际上执行的是 RPC。
            AlgorithmWorkflow workflow = client.newWorkflowStub(AlgorithmWorkflow.class, options);
            workflow.run(req, new TemporalConfig(initialInterval, maximumInterval, backoffCoefficient
                    , maximumAttempts, startToCloseTimeout, heartbeatTimeout));
            log.info("向队列投递Workflow消息 {} {}", sequence, memo);
            //WorkflowStub workflow = client.newUntypedWorkflowStub(workflowId, ...)
            //client.getWorkflowServiceStubs().

            // 异步
//            WorkflowExecution we = WorkflowClient.start(workflow::run, req, new TemporalConfig(initialInterval, maximumInterval, backoffCoefficient
//                    , maximumAttempts, startToCloseTimeout, heartbeatTimeout));
            log.debug("向队列投递Workflow消息成功 {} {}", sequence, memo);
        } catch (WorkflowExecutionAlreadyStarted e) {
            Throwable cause = e.getCause();
            if (cause instanceof StatusRuntimeException) {
                StatusRuntimeException e1 = (StatusRuntimeException) cause;
                if (e1.getStatus().getDescription().contains("Workflow execution is already running.")) {
                    log.warn("Workflow ID 重复，进行自动修复 --------->>>> sequence:{}  memo:{}", sequence, memo);
                    return;
                }
                throw e;
            }
        }
    }


    /**
     * 向工作流发送消息
     *
     * @param req
     */
    public void sendConsumer(WorkerStreamReq req) {
        String sequence = req.getSequence(), memo = req.getMemo(), workflowId = req.getSequence();
        // WorkflowServiceStubs 是一个 gRPC 存根包装器，它与临时服务器的本地 Docker 实例进行对话。
        try {
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setTaskQueue(ALGORITHM_CONSUMER_TASK_QUEUE)
                    // WorkflowId 防止它具有重复的实例，将其删除以进行复制。
                    .setWorkflowId(workflowId)
                    .setWorkflowTaskTimeout(Duration.ofHours(1))
//                    .setWorkflowExecutionTimeout(Duration.ofMinutes(3))
//                    .setWorkflowRunTimeout(Duration.ofMinutes(3))
                    .build();

            // WorkflowStubs 启用对方法的调用，就好像 Workflow 对象是本地的一样，但实际上执行的是 RPC。
            AlgorithmConsumerWorkflow workflow = client.newWorkflowStub(AlgorithmConsumerWorkflow.class, options);
            workflow.run(req, new TemporalConfig(initialInterval, maximumInterval, backoffCoefficient
                    , maximumAttempts, startToCloseTimeout, heartbeatTimeout));
            log.info("向队列投递Workflow消息 {} {}", sequence, memo);
            //WorkflowStub workflow = client.newUntypedWorkflowStub(workflowId, ...)
            //client.getWorkflowServiceStubs().

            // 异步
//            WorkflowExecution we = WorkflowClient.start(workflow::run, req, new TemporalConfig(initialInterval, maximumInterval, backoffCoefficient
//                    , maximumAttempts, startToCloseTimeout, heartbeatTimeout));
            log.debug("向队列投递Workflow消息成功 {} {}", sequence, memo);
        } catch (WorkflowExecutionAlreadyStarted e) {
            Throwable cause = e.getCause();
            if (cause instanceof StatusRuntimeException) {
                StatusRuntimeException e1 = (StatusRuntimeException) cause;
                if (e1.getStatus().getDescription().contains("Workflow execution is already running.")) {
                    log.warn("Workflow ID 重复，进行自动修复 --------->>>> sequence:{}  memo:{}", sequence, memo);
                    return;
                }
                throw e;
            }
        }
    }

    /**
     * 获取 workflow 查询状态
     *
     * @param workId
     */
    public String getWorkFlowExecutionStatus(String workId) {
        WorkflowExecution execution = client.newUntypedWorkflowStub(workId).getExecution();
        String status = getStatusAsString(execution);
        System.out.println(status);
        return status;
    }


    public int getWorkFlowExecutionCount(String workId, String runId) {
        Optional<String> optional;
        if (runId == null || runId.isEmpty()) {
            optional = Optional.empty();
        } else {
            optional = Optional.of(runId);
        }
        WorkflowStub workflowStub = client.newUntypedWorkflowStub(workId,optional,Optional.empty());
        return workflowStub.query("queryCount", Integer.class);
    }


    private static String getStatusAsString(WorkflowExecution execution) {
        DescribeWorkflowExecutionRequest describeWorkflowExecutionRequest =
                DescribeWorkflowExecutionRequest.newBuilder()
                        .setNamespace(client.getOptions().getNamespace())
                        .setExecution(execution)
                        .build();
        DescribeWorkflowExecutionResponse resp = client.getWorkflowServiceStubs().blockingStub()
                .describeWorkflowExecution(describeWorkflowExecutionRequest);
        WorkflowExecutionInfo workflowExecutionInfo = resp.getWorkflowExecutionInfo();
        return workflowExecutionInfo.getStatus().toString();
    }

}
