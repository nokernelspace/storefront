package org.springframework.validation;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface MessageCodeFormatter {
   String format(String errorCode, @Nullable String objectName, @Nullable String field);
}
