package org.springframework.format;

import java.util.Locale;

@FunctionalInterface
public interface Printer<T> {
   String print(T object, Locale locale);
}
