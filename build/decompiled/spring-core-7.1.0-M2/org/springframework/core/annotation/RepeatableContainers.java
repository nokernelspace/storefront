package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;

public abstract class RepeatableContainers {
   static final Map<Class<? extends Annotation>, Object> cache = new ConcurrentReferenceHashMap<>();
   private final @Nullable RepeatableContainers parent;

   private RepeatableContainers(@Nullable RepeatableContainers parent) {
      this.parent = parent;
   }

   public final RepeatableContainers plus(Class<? extends Annotation> repeatable, Class<? extends Annotation> container) {
      return new RepeatableContainers.ExplicitRepeatableContainer(this, repeatable, container);
   }

   @Deprecated(since = "7.0")
   public RepeatableContainers and(Class<? extends Annotation> container, Class<? extends Annotation> repeatable) {
      return this.plus(repeatable, container);
   }

   Annotation @Nullable [] findRepeatedAnnotations(Annotation annotation) {
      return this.parent == null ? null : this.parent.findRepeatedAnnotations(annotation);
   }

   @Contract("null -> false")
   @Override
   public boolean equals(@Nullable Object other) {
      if (other == this) {
         return true;
      } else {
         return other != null && this.getClass() == other.getClass() ? ObjectUtils.nullSafeEquals(this.parent, ((RepeatableContainers)other).parent) : false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(this.parent);
   }

   public static RepeatableContainers standardRepeatables() {
      return RepeatableContainers.StandardRepeatableContainers.INSTANCE;
   }

   public static RepeatableContainers explicitRepeatable(Class<? extends Annotation> repeatable, @Nullable Class<? extends Annotation> container) {
      return new RepeatableContainers.ExplicitRepeatableContainer(null, repeatable, container);
   }

   @Deprecated(since = "7.0")
   public static RepeatableContainers of(Class<? extends Annotation> repeatable, @Nullable Class<? extends Annotation> container) {
      return explicitRepeatable(repeatable, container);
   }

   public static RepeatableContainers none() {
      return RepeatableContainers.NoRepeatableContainers.INSTANCE;
   }

   private static class ExplicitRepeatableContainer extends RepeatableContainers {
      private final Class<? extends Annotation> repeatable;
      private final Class<? extends Annotation> container;
      private final Method valueMethod;

      ExplicitRepeatableContainer(
         @Nullable RepeatableContainers parent, Class<? extends Annotation> repeatable, @Nullable Class<? extends Annotation> container
      ) {
         super(parent);
         Assert.notNull(repeatable, "Repeatable must not be null");
         if (container == null) {
            container = this.deduceContainer(repeatable);
         }

         Method valueMethod = AttributeMethods.forAnnotationType(container).get("value");

         try {
            if (valueMethod == null) {
               throw new NoSuchMethodException("No value method found");
            }

            Class<?> returnType = valueMethod.getReturnType();
            if (returnType.componentType() != repeatable) {
               throw new AnnotationConfigurationException(
                  "Container type [%s] must declare a 'value' attribute for an array of type [%s]"
                     .formatted(ClassUtils.getCanonicalName(container), ClassUtils.getCanonicalName(repeatable))
               );
            }
         } catch (AnnotationConfigurationException ex) {
            throw ex;
         } catch (Throwable ex) {
            throw new AnnotationConfigurationException(
               "Invalid declaration of container type [%s] for repeatable annotation [%s]"
                  .formatted(ClassUtils.getCanonicalName(container), ClassUtils.getCanonicalName(repeatable)),
               ex
            );
         }

         this.repeatable = repeatable;
         this.container = container;
         this.valueMethod = valueMethod;
      }

      private Class<? extends Annotation> deduceContainer(Class<? extends Annotation> repeatable) {
         Repeatable annotation = repeatable.getAnnotation(Repeatable.class);
         Assert.notNull(
            annotation,
            () -> "Annotation type must be a repeatable annotation: failed to resolve container type for " + ClassUtils.getCanonicalName(repeatable)
         );
         return annotation.value();
      }

      @Override
      Annotation @Nullable [] findRepeatedAnnotations(Annotation annotation) {
         return this.container.isAssignableFrom(annotation.annotationType())
            ? (Annotation[])AnnotationUtils.invokeAnnotationMethod(this.valueMethod, annotation)
            : super.findRepeatedAnnotations(annotation);
      }

      @Override
      public boolean equals(@Nullable Object other) {
         if (!super.equals(other)) {
            return false;
         }

         RepeatableContainers.ExplicitRepeatableContainer otherErc = (RepeatableContainers.ExplicitRepeatableContainer)other;
         return this.container.equals(otherErc.container) && this.repeatable.equals(otherErc.repeatable);
      }

      @Override
      public int hashCode() {
         int hashCode = super.hashCode();
         hashCode = 31 * hashCode + this.container.hashCode();
         return 31 * hashCode + this.repeatable.hashCode();
      }
   }

   private static class NoRepeatableContainers extends RepeatableContainers {
      private static final RepeatableContainers.NoRepeatableContainers INSTANCE = new RepeatableContainers.NoRepeatableContainers();

      NoRepeatableContainers() {
         super(null);
      }
   }

   private static class StandardRepeatableContainers extends RepeatableContainers {
      private static final Object NONE = new Object();
      private static final RepeatableContainers.StandardRepeatableContainers INSTANCE = new RepeatableContainers.StandardRepeatableContainers();

      StandardRepeatableContainers() {
         super(null);
      }

      @Override
      Annotation @Nullable [] findRepeatedAnnotations(Annotation annotation) {
         Method method = getRepeatedAnnotationsMethod(annotation.annotationType());
         return method != null ? (Annotation[])AnnotationUtils.invokeAnnotationMethod(method, annotation) : super.findRepeatedAnnotations(annotation);
      }

      private static @Nullable Method getRepeatedAnnotationsMethod(Class<? extends Annotation> annotationType) {
         Object result = cache.computeIfAbsent(annotationType, RepeatableContainers.StandardRepeatableContainers::computeRepeatedAnnotationsMethod);
         return result != NONE ? (Method)result : null;
      }

      private static Object computeRepeatedAnnotationsMethod(Class<? extends Annotation> annotationType) {
         AttributeMethods methods = AttributeMethods.forAnnotationType(annotationType);
         Method method = methods.get("value");
         if (method != null) {
            Class<?> returnType = method.getReturnType();
            if (returnType.isArray()) {
               Class<?> componentType = returnType.componentType();
               if (Annotation.class.isAssignableFrom(componentType) && componentType.isAnnotationPresent(Repeatable.class)) {
                  return method;
               }
            }
         }

         return NONE;
      }
   }
}
