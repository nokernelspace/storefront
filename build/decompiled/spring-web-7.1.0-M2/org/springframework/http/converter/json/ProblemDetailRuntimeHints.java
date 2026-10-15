package org.springframework.http.converter.json;

import java.lang.reflect.Type;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.http.ProblemDetail;
import org.springframework.util.ClassUtils;

class ProblemDetailRuntimeHints implements RuntimeHintsRegistrar {
   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      BindingReflectionHintsRegistrar bindingRegistrar = new BindingReflectionHintsRegistrar();
      bindingRegistrar.registerReflectionHints(hints.reflection(), new Type[]{ProblemDetail.class});
      if (ClassUtils.isPresent("com.fasterxml.jackson.dataformat.xml.XmlMapper", classLoader)) {
         bindingRegistrar.registerReflectionHints(hints.reflection(), new Type[]{ProblemDetailJacksonXmlMixin.class});
      } else if (ClassUtils.isPresent("com.fasterxml.jackson.annotation.JacksonAnnotation", classLoader)) {
         bindingRegistrar.registerReflectionHints(hints.reflection(), new Type[]{ProblemDetailJacksonMixin.class});
      }
   }
}
