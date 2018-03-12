/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.internal;

import static org.mule.runtime.core.api.functional.Either.left;
import static org.mule.runtime.core.api.functional.Either.right;
import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.core.api.functional.Either;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.execution.OnError;
import org.mule.runtime.extension.api.annotation.execution.OnSuccess;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.source.EmitsResponse;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.source.Source;
import org.mule.runtime.extension.api.runtime.source.SourceCallback;
import org.mule.runtime.extension.api.runtime.source.SourceCallbackContext;
import org.mule.runtime.extension.api.runtime.source.SourceCompletionCallback;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.inject.Inject;

@MediaType(value = ANY, strict = false)
@Alias("listener")
@EmitsResponse
public class RoutingSource extends Source<InputStream, Void> {

  private static final String ROUTING_CONTEXT_KEY = "routingContextKey";

  @Inject
  private RoutingManager routingManager;

  @Parameter
  private String routingKey;

  @Override
  public void onStart(SourceCallback<InputStream, Void> sourceCallback) throws MuleException {
    routingManager.registerRoutingListener(routingKey);
    routingManager.validateRoutingListener(routingKey);

    Function<Result<InputStream, Void>, RoutingContext> callback = result -> {
      SourceCallbackContext ctx = sourceCallback.createContext();
      CompletableFuture<Result<InputStream, Void>> completableResult = new CompletableFuture<>();
      final RoutingContext routingContext = new DefaultRoutingContext(completableResult);

      ctx.addVariable(ROUTING_CONTEXT_KEY, routingContext);
      sourceCallback.handle(result, ctx);

      return routingContext;
    };

    routingManager.registerListenerCallback(routingKey, callback);
  }

  @OnSuccess
  public void onSuccess(@Content InputStream response, SourceCallbackContext callbackContext, SourceCompletionCallback completionCallback) {
    DefaultRoutingContext routingContext = getRoutingContext(callbackContext);
    routingContext.setSourceCompletionCallback(completionCallback);
    routingContext.future.complete(Result.<InputStream, Void>builder().output(response).build());
  }

  /**
   * in some case you may want to propagate the exception... in some others you might want to propagate the entire Error....
   * Others will require an error response builder like the Http connector (yes ApiKit, I may be talking to  you!)
   */
  @OnError
  public void onError(Error error, SourceCallbackContext callbackContext, SourceCompletionCallback completionCallback) {
    DefaultRoutingContext routingContext = getRoutingContext(callbackContext);
    routingContext.setSourceCompletionCallback(completionCallback);
    routingContext.future.completeExceptionally(error.getCause());
  }

  private DefaultRoutingContext getRoutingContext(SourceCallbackContext callbackContext) {
    return (DefaultRoutingContext) callbackContext.getVariable(ROUTING_CONTEXT_KEY).orElseThrow(() -> new IllegalStateException("Some nasty bug"));
  }

  @Override
  public void onStop() {
    routingManager.unregisterListenerCallback(routingKey);
  }

  private class DefaultRoutingContext implements RoutingContext {

    private CompletableFuture<Result<InputStream, Void>> future;
    private Lock sourceCompletionCallbackLock = new ReentrantLock();
    private Condition sourceCompletionCallbackSet = sourceCompletionCallbackLock.newCondition();
    private SourceCompletionCallback sourceCompletionCallback;
    private Either<Result<InputStream, Void>, Throwable> outcome;

    public DefaultRoutingContext(CompletableFuture<Result<InputStream, Void>> future) {
      this.future = future;
    }

    @Override
    public void onSuccess(Consumer<Result<InputStream, Void>> consumer) {
      future.whenComplete((v, e) -> {
        if (v != null) {
          consumer.accept(v);
          outcome = left(v);
        }
      });
    }

    @Override
    public void onError(Consumer<Throwable> consumer) {
      future.exceptionally(e -> {
        if (e != null) {
          consumer.accept(e);
          outcome = right(e);
        }
        return null;
      });
    }

    @Override
    public void dispose() {
      if (outcome == null) {
        sourceCompletionCallbackLock.lock();
        try {
          sourceCompletionCallbackSet.await();
        } catch (InterruptedException e) {
          e.printStackTrace();
          //log and continue
        } finally {
          sourceCompletionCallbackLock.unlock();
        }
      }

      outcome.applyLeft(v -> sourceCompletionCallback.success());
      outcome.applyRight(sourceCompletionCallback::error);
    }

    private void setSourceCompletionCallback(SourceCompletionCallback sourceCompletionCallback) {
      this.sourceCompletionCallback = sourceCompletionCallback;
      sourceCompletionCallbackLock.lock();
      try {
        sourceCompletionCallbackSet.signal();
      } finally {
        sourceCompletionCallbackLock.unlock();
      }
    }
  }

}
