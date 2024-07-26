package com.boot.temporal.activity;

import com.boot.common.BeanContext;
import com.boot.common.HeartbeatThread;
import com.boot.common.Shared;
import com.boot.kafka.MyKafkaConsumer;
import com.boot.temporal.AlgorithmWorker;
import com.boot.temporal.manager.Algorithm1Manager;
import com.boot.temporal.manager.BaseManager;
import com.boot.temporal.po.WorkerStreamReq;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.activity.ActivityInfo;
import io.temporal.client.WorkflowClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.boot.common.Shared.CONSUMER_WORK_IDS_REDIS_KEY;
import static com.boot.common.Shared.WORK_IDS_REDIS_KEY;
import static com.boot.temporal.AlgorithmWorker.client;

@Slf4j
public class AlgorithmActivityImpl implements AlgorithmActivity {

    private Algorithm1Manager algorithm1Manager;
    private BaseManager baseManager;
    private AlgorithmWorker AlgorithmWorker;

    private KafkaTemplate<String, String> kafkaTemplate;


    private RedisTemplate redisTemplate;

    private MyKafkaConsumer myKafkaConsumer;

    public AlgorithmActivityImpl(RedisTemplate redisTemplate) {
        log.info("加载初始Bean实例信息");
        baseManager = BeanContext.getBean(BaseManager.class);
        algorithm1Manager = BeanContext.getBean(Algorithm1Manager.class);
        AlgorithmWorker = BeanContext.getBean(AlgorithmWorker.class);
        kafkaTemplate = BeanContext.getBean(KafkaTemplate.class);
        myKafkaConsumer = BeanContext.getBean(MyKafkaConsumer.class);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void send() {
        WorkerStreamReq workerStreamReq = new WorkerStreamReq();
        workerStreamReq.setSequence(UUID.randomUUID().toString());
        workerStreamReq.setMemo("0");
        workerStreamReq.setType(1);
        kafkaTemplate.send("samples-topic", workerStreamReq.getSequence());
        log.info("{} ------------>>>>> 在此处你可以进行【打印】操作。", workerStreamReq);
    }

    @Override
    public WorkerStreamReq algorithm1(WorkerStreamReq req) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        ActivityInfo info = ctx.getInfo();
        String workflowId = info.getWorkflowId();

        HeartbeatThread heartbeatThread = baseManager.startHeartbeatThread(Shared.ALGORITHM1, ctx);

        log.info("{} ------------>>>>> 开始执行【algorithm1】。", workflowId);

        try {
            algorithm1Manager.exec(req);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            heartbeatThread.stop();
        }
        return baseManager.serializationSave(req);
    }

    @Override
    public void print(WorkerStreamReq req) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        ActivityInfo info = ctx.getInfo();
        String workflowId = info.getWorkflowId();
        log.info("{} ------------>>>>> 在此处你可以进行【打印】操作。", workflowId);
    }

    @Override
    public void consumer() {
        List<String> cousmer = myKafkaConsumer.cousmer();
        log.info("{} ------------>>>>> 在此处你可以进行【打印】操作。", "cousmer");
    }

    @Override
    public boolean db(WorkerStreamReq req) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        ActivityInfo info = ctx.getInfo();
        String workflowId = info.getWorkflowId();
        req = baseManager.deserializeGet(req);
        log.info("{} ------------>>>>> 在此处你可以进行【数据持久化】操作。", workflowId);
        return true;
    }

    @Override
    public String redis(WorkerStreamReq req) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        ActivityInfo info = ctx.getInfo();
        String workflowId = info.getWorkflowId();
        req = baseManager.deserializeGet(req);
        log.info("{} ------------>>>>> 在此处你可以进行【Redis】操作。", workflowId);
        return "这是Redis数据";
    }


    @Override
    public void terminate(List<String> workIds) {
        //redisTemplate.opsForSet().add(WORK_IDS_REDIS_KEY, workIds.toArray());
        terminateCommon(workIds, WORK_IDS_REDIS_KEY);
    }

    @Override
    public void terminateConsumer(List<String> workIds) {
        terminateCommon(workIds, CONSUMER_WORK_IDS_REDIS_KEY);

    }

    @Override
    public void terminateHandleConsumer() {
        terminateHandleCommon(CONSUMER_WORK_IDS_REDIS_KEY);
    }

    private void terminateCommon(List<String> workIds, String key) {
        redisTemplate.opsForSet().add(WORK_IDS_REDIS_KEY, workIds.toArray());

    }


    public void terminateHandleCommon(String key) {

        WorkflowClient client = client();
        if (client != null) {
            Set<Object> members = redisTemplate.opsForSet().members(key);
            if (members != null && !members.isEmpty()) {
                for (Object work : members) {
                    try {
                        client.newUntypedWorkflowStub(work.toString()).terminate("Outdated");
                        log.info("terminate workflow success ,workflow id:{}", work);
                    } catch (Exception e) {

                    }
                }
                redisTemplate.opsForSet().remove(key, members.toArray());
            }

        }
    }

    @Override
    public void terminateHandle() {
        terminateHandleCommon(WORK_IDS_REDIS_KEY);
    }


    @Override
    public void clearSerializationCache(String sequence, String memo) {
        baseManager.clearSerializationCache(sequence, memo);
    }
}
