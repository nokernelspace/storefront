package org.springframework.web.method.annotation;

import java.lang.reflect.Method;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;

public class ExceptionHandlerMappingInfo {
   private final Set<Class<? extends Throwable>> exceptionTypes;
   private final Set<MediaType> producibleTypes;
   private final Method handlerMethod;

   ExceptionHandlerMappingInfo(Set<Class<? extends Throwable>> exceptionTypes, Set<MediaType> producibleMediaTypes, Method handlerMethod) {
      Assert.notNull(exceptionTypes, "exceptionTypes should not be null");
      Assert.notNull(producibleMediaTypes, "producibleMediaTypes should not be null");
      Assert.notNull(handlerMethod, "handlerMethod should not be null");
      this.exceptionTypes = exceptionTypes;
      this.producibleTypes = producibleMediaTypes;
      this.handlerMethod = handlerMethod;
   }

   public Method getHandlerMethod() {
      return this.handlerMethod;
   }

   public Set<Class<? extends Throwable>> getExceptionTypes() {
      return this.exceptionTypes;
   }

   public Set<MediaType> getProducibleTypes() {
      return this.producibleTypes;
   }
}
