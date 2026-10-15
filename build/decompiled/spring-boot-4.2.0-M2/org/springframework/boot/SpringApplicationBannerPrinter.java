package org.springframework.boot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class SpringApplicationBannerPrinter {
   static final String BANNER_LOCATION_PROPERTY = "spring.banner.location";
   static final String DEFAULT_BANNER_LOCATION = "banner.txt";
   private static final Banner DEFAULT_BANNER = new SpringBootBanner();
   private final ResourceLoader resourceLoader;
   private final @Nullable Banner fallbackBanner;

   SpringApplicationBannerPrinter(ResourceLoader resourceLoader, @Nullable Banner fallbackBanner) {
      this.resourceLoader = resourceLoader;
      this.fallbackBanner = fallbackBanner;
   }

   Banner print(Environment environment, @Nullable Class<?> sourceClass, Log logger) {
      Banner banner = this.getBanner(environment);

      try {
         logger.info(this.createStringFromBanner(banner, environment, sourceClass));
      } catch (UnsupportedEncodingException ex) {
         logger.warn("Failed to create String for banner", ex);
      }

      return new SpringApplicationBannerPrinter.PrintedBanner(banner, sourceClass);
   }

   Banner print(Environment environment, @Nullable Class<?> sourceClass, PrintStream out) {
      Banner banner = this.getBanner(environment);
      banner.printBanner(environment, sourceClass, out);
      return new SpringApplicationBannerPrinter.PrintedBanner(banner, sourceClass);
   }

   private Banner getBanner(Environment environment) {
      Banner textBanner = this.getTextBanner(environment);
      if (textBanner != null) {
         return textBanner;
      } else {
         return this.fallbackBanner != null ? this.fallbackBanner : DEFAULT_BANNER;
      }
   }

   private @Nullable Banner getTextBanner(Environment environment) {
      String location = environment.getProperty("spring.banner.location", "banner.txt");
      Resource resource = this.resourceLoader.getResource(location);

      try {
         if (resource.exists() && !resource.getURL().toExternalForm().contains("liquibase-core")) {
            return new ResourceBanner(resource);
         }
      } catch (IOException var5) {
      }

      return null;
   }

   private String createStringFromBanner(Banner banner, Environment environment, @Nullable Class<?> mainApplicationClass) throws UnsupportedEncodingException {
      String charset = environment.getProperty("spring.banner.charset", StandardCharsets.UTF_8.name());
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

      try (PrintStream out = new PrintStream(byteArrayOutputStream, false, charset)) {
         banner.printBanner(environment, mainApplicationClass, out);
      }

      return byteArrayOutputStream.toString(charset);
   }

   private static class PrintedBanner implements Banner {
      private final Banner banner;
      private final @Nullable Class<?> sourceClass;

      PrintedBanner(Banner banner, @Nullable Class<?> sourceClass) {
         this.banner = banner;
         this.sourceClass = sourceClass;
      }

      @Override
      public void printBanner(Environment environment, @Nullable Class<?> sourceClass, PrintStream out) {
         sourceClass = sourceClass != null ? sourceClass : this.sourceClass;
         this.banner.printBanner(environment, sourceClass, out);
      }
   }

   static class SpringApplicationBannerPrinterRuntimeHints implements RuntimeHintsRegistrar {
      public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
         hints.resources().registerPattern("banner.txt");
      }
   }
}
