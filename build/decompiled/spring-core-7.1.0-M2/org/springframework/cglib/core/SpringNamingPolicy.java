package org.springframework.cglib.core;

public final class SpringNamingPolicy implements NamingPolicy {
   public static final SpringNamingPolicy INSTANCE = new SpringNamingPolicy();
   private static final String SPRING_LABEL = "$$SpringCGLIB$$";
   private static final String FAST_CLASS_SUFFIX = "FastClass$$";

   private SpringNamingPolicy() {
   }

   @Override
   public String getClassName(String prefix, String source, Object key, Predicate names) {
      if (prefix == null) {
         prefix = "org.springframework.cglib.empty.Object";
      } else if (prefix.startsWith("java.") || prefix.startsWith("javax.")) {
         prefix = "_" + prefix;
      }

      int existingLabel = prefix.indexOf("$$SpringCGLIB$$");
      String base;
      if (existingLabel >= 0) {
         base = prefix.substring(0, existingLabel + "$$SpringCGLIB$$".length());
      } else {
         base = prefix + "$$SpringCGLIB$$";
      }

      boolean isFastClass = source != null && source.endsWith(".FastClass");
      if (isFastClass && !prefix.contains("FastClass$$")) {
         base = base + "FastClass$$";
      }

      int index = 0;
      String attempt = base + index;

      while (names.evaluate(attempt)) {
         attempt = base + index++;
      }

      return attempt;
   }
}
