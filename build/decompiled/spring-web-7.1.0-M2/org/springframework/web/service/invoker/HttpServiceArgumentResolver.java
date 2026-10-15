package org.springframework.web.service.invoker;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;

public interface HttpServiceArgumentResolver {
   boolean resolve(@Nullable Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues);
}
