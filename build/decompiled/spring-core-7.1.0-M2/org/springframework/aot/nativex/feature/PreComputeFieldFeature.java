package org.springframework.aot.nativex.feature;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.regex.Pattern;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

class PreComputeFieldFeature implements Feature {
   private static final boolean verbose = "verbose".equalsIgnoreCase(System.getProperty("spring.native.precompute.log"));
   private static final Pattern[] patterns = new Pattern[]{
      Pattern.compile(Pattern.quote("org.springframework.core.NativeDetector#inNativeImage")),
      Pattern.compile(Pattern.quote("org.springframework.cglib.core.AbstractClassGenerator#inNativeImage")),
      Pattern.compile(Pattern.quote("org.springframework.aot.AotDetector#inNativeImage")),
      Pattern.compile(Pattern.quote("org.springframework.") + ".*#.*Present"),
      Pattern.compile(Pattern.quote("org.springframework.") + ".*#.*PRESENT"),
      Pattern.compile(Pattern.quote("reactor.core.") + ".*#.*Available"),
      Pattern.compile(Pattern.quote("org.apache.commons.logging.LogAdapter") + "#.*Present")
   };
   private final ThrowawayClassLoader throwawayClassLoader = new ThrowawayClassLoader(this.getClass().getClassLoader());

   public void beforeAnalysis(BeforeAnalysisAccess access) {
      access.registerSubtypeReachabilityHandler(this::iterateFields, Object.class);
   }

   private void iterateFields(DuringAnalysisAccess access, Class<?> subtype) {
      try {
         for (Field field : subtype.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)
               && Modifier.isFinal(modifiers)
               && !field.isEnumConstant()
               && (field.getType() == boolean.class || field.getType() == Boolean.class)) {
               String fieldIdentifier = field.getDeclaringClass().getName() + "#" + field.getName();

               for (Pattern pattern : patterns) {
                  if (pattern.matcher(fieldIdentifier).matches()) {
                     try {
                        Object fieldValue = this.provideFieldValue(field);
                        access.registerFieldValueTransformer(field, (receiver, originalValue) -> fieldValue);
                        if (verbose) {
                           System.out.println("Field " + fieldIdentifier + " set to " + fieldValue + " at build time");
                        }
                     } catch (Throwable ex) {
                        if (verbose) {
                           System.out
                              .println("Field " + fieldIdentifier + " will be evaluated at runtime due to this error during build time evaluation: " + ex);
                        }
                     }
                  }
               }
            }
         }
      } catch (NoClassDefFoundError var15) {
      }
   }

   private Object provideFieldValue(Field field) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
      Class<?> throwawayClass = this.throwawayClassLoader.loadClass(field.getDeclaringClass().getName());
      Field throwawayField = throwawayClass.getDeclaredField(field.getName());
      throwawayField.setAccessible(true);
      return throwawayField.get(null);
   }
}
