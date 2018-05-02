/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.core.api.util.queue.Queue;
import org.mule.runtime.core.api.util.queue.QueueManager;
import org.mule.runtime.core.api.util.queue.QueueSession;
import org.mule.runtime.extension.api.annotation.execution.OnTerminate;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.source.Source;
import org.mule.runtime.extension.api.runtime.source.SourceCallback;
import org.mule.runtime.extension.api.runtime.source.SourceCallbackContext;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VMRoutingSource extends Source<String, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(VMRoutingSource.class);

  private static final String QUEUE_NAME = "reliabilityQueue";
  private static final long POLL_TIMEOUT = 5000;
  private static final long SHUTDOWN_TIMEOUT = POLL_TIMEOUT * 2;
  private static final String QUEUE_SESSION_VAR = "queueSession";

  @Inject
  private QueueManager queueManager;

  @Inject
  private SchedulerService schedulerService;

  @Inject
  private SchedulerConfig schedulerConfig;

  private Queue queue;
  private Scheduler scheduler;
  private ComponentLocation location;
  private AtomicBoolean stopped = new AtomicBoolean(false);

  @Override
  public void onStart(SourceCallback<String, Void> sourceCallback) {
    stopped.set(false);
    scheduler = schedulerService.customScheduler(schedulerConfig
                                                     .withMaxConcurrentTasks(1)
                                                     .withName("rosetta-listener-flow " + location.getRootContainerName())
                                                     .withWaitAllowed(true)
                                                     .withShutdownTimeout(SHUTDOWN_TIMEOUT, MILLISECONDS));

    scheduler.submit(() -> listen(sourceCallback));
  }

  @Override
  public void onStop() {
    stopped.set(true);
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
  }

  @OnTerminate
  public void onTerminate(SourceCallbackContext ctx) {
    ctx.<QueueSession>getVariable(QUEUE_SESSION_VAR).ifPresent(session -> {
      try {
        session.commit();
      } catch (Exception e) {
        // handle()
      }
    });
  }

  public void onNewMessage(InputStream inputStream) {
    try {
      queueManager.getQueueSession().getQueue(QUEUE_NAME).offer(IOUtils.toString(inputStream), 5000);
    } catch (InterruptedException e) {
      // handle error
    }
  }

  private void listen(SourceCallback<String, Void> sourceCallback) {
    while (!stopped.get()) {
      QueueSession queueSession = queueManager.getQueueSession();
      try {
        queueSession.begin();
      } catch (Exception e) {
        LOGGER.error("Could not start transaction", e);
        // probably wait some time before retrying or add some other type of error handling policy
      }

      Queue queue = queueSession.getQueue(QUEUE_NAME);
      String document = null;
      try {
        document = (String) queue.poll(POLL_TIMEOUT);
      } catch (Exception e) {
        LOGGER.error("Could not poll element", e);
        // probably wait some time before retrying or add some other type of error handling policy
      }

      SourceCallbackContext ctx = sourceCallback.createContext();
      ctx.addVariable(QUEUE_SESSION_VAR, queueSession);

      if (stopped.get()) {
        return;
      }
      sourceCallback.handle(Result.<String, Void>builder().output(document).build(), ctx);
    }
  }
}
