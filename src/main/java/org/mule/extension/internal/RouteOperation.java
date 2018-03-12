package org.mule.extension.internal;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.runtime.operation.FlowListener;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.process.CompletionCallback;

import java.io.InputStream;

import javax.inject.Inject;


/**
 * This class is a container for operations, every public method in this class will be taken as an extension operation.
 */
public class RouteOperation implements Initialisable, Startable {

  @Inject
  private RoutingManager routingManager;

  @Parameter
  private String routingKey;

  @Override
  public void initialise() {
    routingManager.registerRoutingTarget(routingKey);
  }

  @Override
  public void start() {
    routingManager.validateRoutingTarget(routingKey);
  }

  /**
   * Example of an operation that uses the configuration and a connection instance to perform some action.
   */
  @MediaType(value = ANY, strict = false)
  public void route(@Content InputStream content,
                    CompletionCallback<InputStream, Void> completionCallback,
                    FlowListener flowListener){
    Result<InputStream, Void> result = Result.<InputStream, Void>builder().output(content).build();
    RoutingContext routingContext = routingManager.route(routingKey, result);
    routingContext.onSuccess(completionCallback::success);
    routingContext.onError(completionCallback::error);
    flowListener.onComplete(routingContext::dispose);
  }

}
