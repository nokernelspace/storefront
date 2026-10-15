package org.springframework.web.method.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

public class HandlerMethodReturnValueHandlerComposite implements HandlerMethodReturnValueHandler {
   private final List<HandlerMethodReturnValueHandler> returnValueHandlers = new ArrayList<>();

   public List<HandlerMethodReturnValueHandler> getHandlers() {
      return Collections.unmodifiableList(this.returnValueHandlers);
   }

   @Override
   public boolean supportsReturnType(MethodParameter returnType) {
      return this.getReturnValueHandler(returnType) != null;
   }

   private @Nullable HandlerMethodReturnValueHandler getReturnValueHandler(MethodParameter returnType) {
      for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
         if (handler.supportsReturnType(returnType)) {
            return handler;
         }
      }

      return null;
   }

   @Override
   public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
      HandlerMethodReturnValueHandler handler = this.selectHandler(returnValue, returnType);
      if (handler == null) {
         throw new IllegalArgumentException("Unknown return value type: " + returnType.getParameterType().getName());
      }

      handler.handleReturnValue(returnValue, returnType, mavContainer, webRequest);
   }

   private @Nullable HandlerMethodReturnValueHandler selectHandler(@Nullable Object value, MethodParameter returnType) {
      boolean isAsyncValue = this.isAsyncReturnValue(value, returnType);

      for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
         if ((!isAsyncValue || handler instanceof AsyncHandlerMethodReturnValueHandler) && handler.supportsReturnType(returnType)) {
            return handler;
         }
      }

      return null;
   }

   private boolean isAsyncReturnValue(@Nullable Object value, MethodParameter returnType) {
      for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
         if (handler instanceof AsyncHandlerMethodReturnValueHandler asyncHandler && asyncHandler.isAsyncReturnValue(value, returnType)) {
            return true;
         }
      }

      return false;
   }

   public HandlerMethodReturnValueHandlerComposite addHandler(HandlerMethodReturnValueHandler handler) {
      this.returnValueHandlers.add(handler);
      return this;
   }

   public HandlerMethodReturnValueHandlerComposite addHandlers(@Nullable List<? extends HandlerMethodReturnValueHandler> handlers) {
      if (handlers != null) {
         this.returnValueHandlers.addAll(handlers);
      }

      return this;
   }
}
