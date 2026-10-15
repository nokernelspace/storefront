package org.springframework.boot.context.properties.bind;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class BindableRuntimeHintsRegistrar implements RuntimeHintsRegistrar {
   private static final Log logger = LogFactory.getLog(BindableRuntimeHintsRegistrar.class);
   private final Bindable<?>[] bindables;

   protected BindableRuntimeHintsRegistrar(Class<?>... types) {
      this(Stream.of(types).map(Bindable::of).toArray(Bindable[]::new));
   }

   protected BindableRuntimeHintsRegistrar(Bindable<?>... bindables) {
      this.bindables = bindables;
   }

   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      this.registerHints(hints);
   }

   public void registerHints(RuntimeHints hints) {
      for (Bindable<?> bindable : this.bindables) {
         try {
            new BindableRuntimeHintsRegistrar.Processor(bindable).process(hints.reflection());
         } catch (Exception ex) {
            logger.debug("Skipping hints for " + bindable, ex);
         }
      }
   }

   public static BindableRuntimeHintsRegistrar forTypes(Iterable<Class<?>> types) {
      Assert.notNull(types, "'types' must not be null");
      return forTypes(StreamSupport.stream(types.spliterator(), false).toArray(Class[]::new));
   }

   public static BindableRuntimeHintsRegistrar forTypes(Class<?>... types) {
      return new BindableRuntimeHintsRegistrar(types);
   }

   public static BindableRuntimeHintsRegistrar forBindables(Iterable<Bindable<?>> bindables) {
      Assert.notNull(bindables, "'bindables' must not be null");
      return forBindables(StreamSupport.stream(bindables.spliterator(), false).toArray(Bindable[]::new));
   }

   public static BindableRuntimeHintsRegistrar forBindables(Bindable<?>... bindables) {
      return new BindableRuntimeHintsRegistrar(bindables);
   }

   private static final class KotlinDelegate {
      static void handleConstructor(ReflectionHints hints, Constructor<?> constructor) {
         KClass<?> kClass = JvmClassMappingKt.getKotlinClass(constructor.getDeclaringClass());
         if (kClass.isData()) {
            hints.registerType(constructor.getDeclaringClass(), new MemberCategory[]{MemberCategory.INVOKE_DECLARED_CONSTRUCTORS});
         } else {
            hints.registerConstructor(constructor, ExecutableMode.INVOKE);
         }
      }
   }

   private static final class Processor {
      private final Class<?> type;
      private final @Nullable Constructor<?> bindConstructor;
      private final JavaBeanBinder.BeanProperties bean;
      private final Set<Class<?>> seen;

      Processor(Bindable<?> bindable) {
         this(bindable, false, new HashSet<>());
      }

      private Processor(Bindable<?> bindable, boolean nestedType, Set<Class<?>> seen) {
         this.type = getRawClass(bindable);
         this.bindConstructor = bindable.getBindMethod() != BindMethod.JAVA_BEAN
            ? BindConstructorProvider.DEFAULT.getBindConstructor(getBindableType(bindable), nestedType)
            : null;
         this.bean = JavaBeanBinder.BeanProperties.of(bindable);
         this.seen = seen;
      }

      private static Class<?> getBindableType(Bindable<?> bindable) {
         Class<?> resolved = bindable.getType().resolve();
         Assert.state(resolved != null, "'resolved' must not be null");
         return resolved;
      }

      private static Class<?> getRawClass(Bindable<?> bindable) {
         Class<?> rawClass = bindable.getType().getRawClass();
         Assert.state(rawClass != null, "'rawClass' must not be null");
         return rawClass;
      }

      void process(ReflectionHints hints) {
         if (!this.seen.contains(this.type)) {
            this.seen.add(this.type);
            this.handleConstructor(hints);
            if (this.bindConstructor != null) {
               this.handleValueObjectProperties(hints);
            } else if (this.bean != null && !this.bean.getProperties().isEmpty()) {
               this.handleJavaBeanProperties(hints);
            }
         }
      }

      private void handleConstructor(ReflectionHints hints) {
         if (this.bindConstructor != null) {
            if (KotlinDetector.isKotlinType(this.bindConstructor.getDeclaringClass())) {
               BindableRuntimeHintsRegistrar.KotlinDelegate.handleConstructor(hints, this.bindConstructor);
            } else {
               hints.registerConstructor(this.bindConstructor, ExecutableMode.INVOKE);
            }
         } else {
            Arrays.stream(this.type.getDeclaredConstructors())
               .filter(this::hasNoParameters)
               .findFirst()
               .ifPresent(constructor -> hints.registerConstructor(constructor, ExecutableMode.INVOKE));
         }
      }

      private boolean hasNoParameters(Constructor<?> candidate) {
         return candidate.getParameterCount() == 0;
      }

      private void handleValueObjectProperties(ReflectionHints hints) {
         Assert.state(this.bindConstructor != null, "'bindConstructor' must not be null");

         for (int i = 0; i < this.bindConstructor.getParameterCount(); i++) {
            String propertyName = this.bindConstructor.getParameters()[i].getName();
            ResolvableType propertyType = ResolvableType.forConstructorParameter(this.bindConstructor, i);
            this.handleProperty(hints, propertyName, propertyType);
         }
      }

      private void handleJavaBeanProperties(ReflectionHints hints) {
         Map<String, JavaBeanBinder.BeanProperty> properties = this.bean.getProperties();
         properties.forEach((name, property) -> {
            Method getter = property.getGetter();
            if (getter != null) {
               hints.registerMethod(getter, ExecutableMode.INVOKE);
            }

            Method setter = property.getSetter();
            if (setter != null) {
               hints.registerMethod(setter, ExecutableMode.INVOKE);
            }

            Field field = property.getField();
            if (field != null) {
               hints.registerField(field);
            }

            this.handleProperty(hints, name, property.getType());
         });
      }

      private void handleProperty(ReflectionHints hints, String propertyName, ResolvableType propertyType) {
         Class<?> propertyClass = propertyType.resolve();
         if (propertyClass != null) {
            if (!propertyClass.equals(this.type)) {
               Class<?> componentType = this.getComponentClass(propertyType);
               if (componentType != null) {
                  if (!this.isJavaType(componentType)) {
                     this.processNested(componentType, hints);
                  }
               } else if (this.isNestedType(propertyName, propertyClass)) {
                  this.processNested(propertyClass, hints);
               }
            }
         }
      }

      private void processNested(Class<?> type, ReflectionHints hints) {
         new BindableRuntimeHintsRegistrar.Processor(Bindable.of(type), true, this.seen).process(hints);
      }

      private @Nullable Class<?> getComponentClass(ResolvableType type) {
         ResolvableType componentType = this.getComponentType(type);
         if (componentType == null) {
            return null;
         } else {
            return this.isContainer(componentType) ? this.getComponentClass(componentType) : componentType.toClass();
         }
      }

      private @Nullable ResolvableType getComponentType(ResolvableType type) {
         if (type.isArray()) {
            return type.getComponentType();
         } else if (this.isCollection(type)) {
            return type.asCollection().getGeneric(new int[0]);
         } else {
            return this.isMap(type) ? type.asMap().getGeneric(new int[]{1}) : null;
         }
      }

      private boolean isContainer(ResolvableType type) {
         return type.isArray() || this.isCollection(type) || this.isMap(type);
      }

      private boolean isCollection(ResolvableType type) {
         return Collection.class.isAssignableFrom(type.toClass());
      }

      private boolean isMap(ResolvableType type) {
         return Map.class.isAssignableFrom(type.toClass());
      }

      private boolean isNestedType(String propertyName, Class<?> propertyType) {
         Class<?> declaringClass = propertyType.getDeclaringClass();
         if (declaringClass != null && isNested(declaringClass, this.type)) {
            return true;
         }

         Field field = ReflectionUtils.findField(this.type, propertyName);
         return field != null && MergedAnnotations.from(field).isPresent(Nested.class);
      }

      private static boolean isNested(Class<?> type, Class<?> candidate) {
         return type.isAssignableFrom(candidate) ? true : candidate.getDeclaringClass() != null && isNested(type, candidate.getDeclaringClass());
      }

      private boolean isJavaType(Class<?> candidate) {
         return candidate.getPackageName().startsWith("java.");
      }
   }
}
