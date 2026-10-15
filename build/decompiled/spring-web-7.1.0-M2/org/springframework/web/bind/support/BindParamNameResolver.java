package org.springframework.web.bind.support;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;
import org.springframework.validation.DataBinder.NameResolver;
import org.springframework.web.bind.annotation.BindParam;

public final class BindParamNameResolver implements NameResolver {
   public @Nullable String resolveName(MethodParameter parameter) {
      BindParam bindParam = (BindParam)parameter.getParameterAnnotation(BindParam.class);
      return bindParam != null && StringUtils.hasText(bindParam.value()) ? bindParam.value() : null;
   }
}
