package org.springframework.aot.hint.annotation;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class RegisterReflectionReflectiveProcessor implements ReflectiveProcessor {
   private static final Log logger = LogFactory.getLog(RegisterReflectionReflectiveProcessor.class);

   @Override
   public final void registerReflectionHints(ReflectionHints hints, AnnotatedElement element) {
      RegisterReflection annotation = AnnotatedElementUtils.getMergedAnnotation(element, RegisterReflection.class);
      Assert.notNull(annotation, () -> "Element must be annotated with @RegisterReflection: " + element);
      RegisterReflectionReflectiveProcessor.ReflectionRegistration registration = this.parse(element, annotation);
      this.registerReflectionHints(hints, registration);
   }

   protected RegisterReflectionReflectiveProcessor.ReflectionRegistration parse(AnnotatedElement element, RegisterReflection annotation) {
      List<Class<?>> allClassNames = new ArrayList<>();
      allClassNames.addAll(Arrays.asList(annotation.classes()));
      allClassNames.addAll(Arrays.stream(annotation.classNames()).map(this::loadClass).filter(Objects::nonNull).toList());
      if (allClassNames.isEmpty()) {
         if (!(element instanceof Class<?> clazz)) {
            throw new IllegalStateException("At least one class must be specified: " + element);
         }

         allClassNames.add(clazz);
      }

      return new RegisterReflectionReflectiveProcessor.ReflectionRegistration(allClassNames.toArray(new Class[0]), annotation.memberCategories());
   }

   protected void registerReflectionHints(ReflectionHints hints, RegisterReflectionReflectiveProcessor.ReflectionRegistration registration) {
      for (Class<?> target : registration.classes) {
         this.registerReflectionHints(hints, target, registration.memberCategories);
      }
   }

   protected void registerReflectionHints(ReflectionHints hints, Class<?> target, MemberCategory[] memberCategories) {
      hints.registerType(target, type -> type.withMembers(memberCategories));
   }

   private @Nullable Class<?> loadClass(String className) {
      try {
         return ClassUtils.forName(className, this.getClass().getClassLoader());
      } catch (Exception ex) {
         if (logger.isWarnEnabled()) {
            logger.warn("Ignoring '" + className + "': " + ex);
         }

         return null;
      }
   }

   protected record ReflectionRegistration(Class<?>[] classes, MemberCategory[] memberCategories) {
   }
}
