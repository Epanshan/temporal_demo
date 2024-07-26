package com.boot.temporal.workflow;

import com.boot.temporal.po.WorkerStreamReq;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface HelloChildConsumerWorkFlow {

  @WorkflowMethod
  void consumer();
}
