package org.springframework.format;

import java.lang.annotation.Annotation;
import java.util.Set;

public interface AnnotationFormatterFactory<A extends Annotation> {
   Set<Class<?>> getFieldTypes();

   Printer<?> getPrinter(A annotation, Class<?> fieldType);

   Parser<?> getParser(A annotation, Class<?> fieldType);
}
