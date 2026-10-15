package org.springframework.format;

import java.lang.annotation.Annotation;
import org.springframework.core.convert.converter.ConverterRegistry;

public interface FormatterRegistry extends ConverterRegistry {
   void addPrinter(Printer<?> printer);

   void addParser(Parser<?> parser);

   void addFormatter(Formatter<?> formatter);

   void addFormatterForFieldType(Class<?> fieldType, Formatter<?> formatter);

   void addFormatterForFieldType(Class<?> fieldType, Printer<?> printer, Parser<?> parser);

   void addFormatterForFieldAnnotation(AnnotationFormatterFactory<? extends Annotation> annotationFormatterFactory);
}
