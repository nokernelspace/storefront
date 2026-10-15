package org.springframework.validation;

import org.jspecify.annotations.Nullable;

public interface MessageCodesResolver {
   String[] resolveMessageCodes(String errorCode, String objectName);

   String[] resolveMessageCodes(String errorCode, String objectName, String field, @Nullable Class<?> fieldType);
}
