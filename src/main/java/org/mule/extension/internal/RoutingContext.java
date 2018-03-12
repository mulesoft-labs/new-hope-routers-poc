package org.mule.extension.internal;

import org.mule.runtime.extension.api.runtime.operation.Result;

import java.io.InputStream;
import java.util.function.Consumer;

public interface RoutingContext {

  void onSuccess(Consumer<Result<InputStream, Void>> consumer);

  void onError(Consumer<Throwable> consumer);

  void dispose();

}
