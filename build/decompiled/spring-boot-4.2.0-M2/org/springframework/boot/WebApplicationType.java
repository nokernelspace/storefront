package org.springframework.boot;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;

public enum WebApplicationType {
   NONE,
   SERVLET,
   REACTIVE;

   private static final String[] SERVLET_INDICATOR_CLASSES = new String[]{
      "jakarta.servlet.Servlet", "org.springframework.web.context.ConfigurableWebApplicationContext"
   };

   public static WebApplicationType deduce() {
      for (WebApplicationType.Deducer deducer : SpringFactoriesLoader.forDefaultResourceLocation().load(WebApplicationType.Deducer.class)) {
         WebApplicationType deduced = deducer.deduceWebApplicationType();
         if (deduced != null) {
            return deduced;
         }
      }

      return isServletApplication() ? SERVLET : NONE;
   }

   private static boolean isServletApplication() {
      for (String servletIndicatorClass : SERVLET_INDICATOR_CLASSES) {
         if (!ClassUtils.isPresent(servletIndicatorClass, null)) {
            return false;
         }
      }

      return true;
   }

   @FunctionalInterface
   public interface Deducer {
      @Nullable WebApplicationType deduceWebApplicationType();
   }

   static class WebApplicationTypeRuntimeHints implements RuntimeHintsRegistrar {
      public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
         for (String servletIndicatorClass : WebApplicationType.SERVLET_INDICATOR_CLASSES) {
            this.registerTypeIfPresent(servletIndicatorClass, classLoader, hints);
         }
      }

      private void registerTypeIfPresent(String typeName, @Nullable ClassLoader classLoader, RuntimeHints hints) {
         if (ClassUtils.isPresent(typeName, classLoader)) {
            hints.reflection().registerType(TypeReference.of(typeName), new MemberCategory[0]);
         }
      }
   }
}
