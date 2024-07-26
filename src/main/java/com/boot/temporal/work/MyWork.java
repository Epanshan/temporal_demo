package com.boot.temporal.work;

import com.boot.temporal.AlgorithmWorker;
import com.boot.temporal.po.WorkerStreamReq;
import io.temporal.client.WorkflowClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
public class MyWork extends Thread {


    private WorkflowClient client;

    private AlgorithmWorker algorithmWorker;

    private int type;

    private String lastWorkId;

    private long waitTime;


    public MyWork(WorkflowClient client, AlgorithmWorker algorithmWorker, int type) {
        super();
        this.client = client;
        this.algorithmWorker = algorithmWorker;
        this.type = type;
        this.waitTime = type == 1 ? 2L : 3L;

    }

    public void run() {

        while (true) {
            try {
                terminate();
                String runId = UUID.randomUUID().toString();
                lastWorkId = runId;
                WorkerStreamReq workerStreamReq = new WorkerStreamReq();
                workerStreamReq.setSequence(runId);
                workerStreamReq.setMemo("0");
                workerStreamReq.setType(1);
                long toEpochMilli = LocalDateTime.now().plusSeconds(waitTime).toInstant(ZoneOffset.of("+8")).toEpochMilli();

                if (type == 1) {
                    algorithmWorker.send(workerStreamReq);

                } else {
                    algorithmWorker.sendConsumer(workerStreamReq);
                }
                long nextTime = toEpochMilli - System.currentTimeMillis();
                if (nextTime > 0) {
                    Thread.sleep(nextTime);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println(e.getLocalizedMessage());
            }

        }

    }

    private void terminate() {
        if (lastWorkId != null) {
            try {
                client.newUntypedWorkflowStub(lastWorkId).terminate("Outdated");
                log.info("workflow terminate success,workId is:{}", lastWorkId);
            } catch (Exception e) {
                log.warn("workflow terminate failed ,workId is:{},error message is:{}", lastWorkId, e.getMessage());
            }
        }


    }


}
