package org.springframework.web.method.annotation;

import jakarta.servlet.ServletException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.web.context.request.NativeWebRequest;

public class ExpressionValueMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver {
   public ExpressionValueMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory) {
      super(beanFactory);
   }

   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return parameter.hasParameterAnnotation(Value.class);
   }

   @Override
   protected AbstractNamedValueMethodArgumentResolver.NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
      Value ann = (Value)parameter.getParameterAnnotation(Value.class);
      Assert.state(ann != null, "No Value annotation");
      return new ExpressionValueMethodArgumentResolver.ExpressionValueNamedValueInfo(ann);
   }

   @Override
   protected @Nullable Object resolveName(String name, MethodParameter parameter, NativeWebRequest webRequest) throws Exception {
      return null;
   }

   @Override
   protected void handleMissingValue(String name, MethodParameter parameter) throws ServletException {
      throw new UnsupportedOperationException("@Value is never required: " + parameter.getMethod());
   }

   private static final class ExpressionValueNamedValueInfo extends AbstractNamedValueMethodArgumentResolver.NamedValueInfo {
      private ExpressionValueNamedValueInfo(Value annotation) {
         super("@Value", false, annotation.value());
      }
   }
}
