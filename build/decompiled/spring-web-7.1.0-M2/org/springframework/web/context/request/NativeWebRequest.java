package org.springframework.web.context.request;

import org.jspecify.annotations.Nullable;

public interface NativeWebRequest extends WebRequest {
   Object getNativeRequest();

   @Nullable Object getNativeResponse();

   <T> @Nullable T getNativeRequest(@Nullable Class<T> requiredType);

   <T> @Nullable T getNativeResponse(@Nullable Class<T> requiredType);
}
