package org.springframework.boot;

public class AotInitializerNotFoundException extends RuntimeException {
   private final Class<?> mainClass;

   public AotInitializerNotFoundException(Class<?> mainClass, String initializerClassName) {
      super("Startup with AOT mode enabled failed: AOT initializer %s could not be found".formatted(initializerClassName));
      this.mainClass = mainClass;
   }

   public Class<?> getMainClass() {
      return this.mainClass;
   }
}
