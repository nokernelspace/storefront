package org.springframework.core.convert.converter;

import org.jspecify.annotations.Nullable;

public interface ConverterFactory<S, R> {
   <T extends R> Converter<S, ? extends @Nullable T> getConverter(Class<T> targetType);
}
