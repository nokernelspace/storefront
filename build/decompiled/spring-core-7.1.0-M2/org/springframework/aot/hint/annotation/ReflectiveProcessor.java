package org.springframework.aot.hint.annotation;

import java.lang.reflect.AnnotatedElement;
import org.springframework.aot.hint.ReflectionHints;

public interface ReflectiveProcessor {
   void registerReflectionHints(ReflectionHints hints, AnnotatedElement element);
}
