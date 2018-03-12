/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.internal;

import org.mule.runtime.extension.api.runtime.operation.Result;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class RoutingManager {

  private List<String> routingTargetKeys = new ArrayList<>();
  private List<String> routingListeners = new ArrayList<>();
  private Map<String, Function<Result<InputStream, Void>, RoutingContext>> callbacks = new HashMap<>();

  public void registerRoutingTarget(String routingTargetKey) {
    routingTargetKeys.add(routingTargetKey);
  }

  public void registerRoutingListener(String routingListenerKey) {
    routingListeners.add(routingListenerKey);
  }

  public void registerListenerCallback(String routingKey, Function<Result<InputStream, Void>, RoutingContext> callback) {
    callbacks.put(routingKey, callback);
  }

  public void unregisterListenerCallback(String routingKey) {
    callbacks.remove(routingKey);
  }

  public void validateRoutingTarget(String routingKey) {
    // validate that for the routingKey there's one and only one listener with the same key
  }

  public void validateRoutingListener(String routingKey) {
    // validate that for the routing key there's at least one route operation pointing to it
    // validate that there are no two listeners with the same routing key
  }

  public RoutingContext route(String routingKey, Result<InputStream, Void> result) {
    return callbacks.get(routingKey).apply(result);
  }
}
