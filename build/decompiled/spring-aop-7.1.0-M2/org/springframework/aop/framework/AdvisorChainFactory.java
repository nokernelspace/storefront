package org.springframework.aop.framework;

import java.lang.reflect.Method;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface AdvisorChainFactory {
   List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, Method method, @Nullable Class<?> targetClass);
}
