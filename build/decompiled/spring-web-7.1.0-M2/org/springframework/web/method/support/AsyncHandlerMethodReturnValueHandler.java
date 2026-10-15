package org.springframework.web.method.support;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;

public interface AsyncHandlerMethodReturnValueHandler extends HandlerMethodReturnValueHandler {
   boolean isAsyncReturnValue(@Nullable Object returnValue, MethodParameter returnType);
}
