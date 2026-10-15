package org.springframework.core.io.support;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.KotlinDetector;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.UrlResource;
import org.springframework.core.log.LogMessage;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class SpringFactoriesLoader {
   public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";
   private static final SpringFactoriesLoader.FailureHandler THROWING_FAILURE_HANDLER = SpringFactoriesLoader.FailureHandler.throwing();
   private static final Log logger = LogFactory.getLog(SpringFactoriesLoader.class);
   static final Map<ClassLoader, Map<String, SpringFactoriesLoader.Factories>> cache = new ConcurrentReferenceHashMap<>();
   private final @Nullable ClassLoader classLoader;
   private final Map<String, List<String>> factories;

   protected SpringFactoriesLoader(@Nullable ClassLoader classLoader, Map<String, List<String>> factories) {
      this.classLoader = classLoader;
      this.factories = factories;
   }

   public <T> List<T> load(Class<T> factoryType) {
      return this.load(factoryType, null, null);
   }

   public <T> List<T> load(Class<T> factoryType, SpringFactoriesLoader.@Nullable ArgumentResolver argumentResolver) {
      return this.load(factoryType, argumentResolver, null);
   }

   public <T> List<T> load(Class<T> factoryType, SpringFactoriesLoader.@Nullable FailureHandler failureHandler) {
      return this.load(factoryType, null, failureHandler);
   }

   public <T> List<T> load(
      Class<T> factoryType, SpringFactoriesLoader.@Nullable ArgumentResolver argumentResolver, SpringFactoriesLoader.@Nullable FailureHandler failureHandler
   ) {
      Assert.notNull(factoryType, "'factoryType' must not be null");
      List<String> implementationNames = this.loadFactoryNames(factoryType);
      logger.trace(LogMessage.format("Loaded [%s] names: %s", factoryType.getName(), implementationNames));
      List<T> result = new ArrayList<>(implementationNames.size());
      SpringFactoriesLoader.FailureHandler failureHandlerToUse = failureHandler != null ? failureHandler : THROWING_FAILURE_HANDLER;

      for (String implementationName : implementationNames) {
         T factory = this.instantiateFactory(implementationName, factoryType, argumentResolver, failureHandlerToUse);
         if (factory != null) {
            result.add(factory);
         }
      }

      AnnotationAwareOrderComparator.sort(result);
      return result;
   }

   private List<String> loadFactoryNames(Class<?> factoryType) {
      return this.factories.getOrDefault(factoryType.getName(), Collections.emptyList());
   }

   protected <T> @Nullable T instantiateFactory(
      String implementationName,
      Class<T> type,
      SpringFactoriesLoader.@Nullable ArgumentResolver argumentResolver,
      SpringFactoriesLoader.FailureHandler failureHandler
   ) {
      try {
         Class<?> factoryImplementationClass = ClassUtils.forName(implementationName, this.classLoader);
         Assert.isTrue(
            type.isAssignableFrom(factoryImplementationClass),
            () -> "Class [%s] is not assignable to factory type [%s]".formatted(implementationName, type.getName())
         );
         SpringFactoriesLoader.FactoryInstantiator<T> factoryInstantiator = SpringFactoriesLoader.FactoryInstantiator.forClass(factoryImplementationClass);
         return factoryInstantiator.instantiate(argumentResolver);
      } catch (Throwable ex) {
         failureHandler.handleFailure(type, implementationName, ex);
         return null;
      }
   }

   public static <T> List<T> loadFactories(Class<T> factoryType, @Nullable ClassLoader classLoader) {
      return forDefaultResourceLocation(classLoader).load(factoryType);
   }

   @Deprecated(since = "6.0")
   public static List<String> loadFactoryNames(Class<?> factoryType, @Nullable ClassLoader classLoader) {
      return forDefaultResourceLocation(classLoader).loadFactoryNames(factoryType);
   }

   public static SpringFactoriesLoader forDefaultResourceLocation() {
      return forDefaultResourceLocation(null);
   }

   public static SpringFactoriesLoader forDefaultResourceLocation(@Nullable ClassLoader classLoader) {
      return forResourceLocation("META-INF/spring.factories", classLoader);
   }

   public static SpringFactoriesLoader forResourceLocation(String resourceLocation) {
      return forResourceLocation(resourceLocation, null);
   }

   public static SpringFactoriesLoader forResourceLocation(String resourceLocation, @Nullable ClassLoader classLoader) {
      Assert.hasText(resourceLocation, "'resourceLocation' must not be empty");
      ClassLoader resourceClassLoader = classLoader != null ? classLoader : SpringFactoriesLoader.class.getClassLoader();
      Map<String, SpringFactoriesLoader.Factories> factoriesCache = cache.computeIfAbsent(resourceClassLoader, key -> new ConcurrentReferenceHashMap<>());
      SpringFactoriesLoader.Factories factories = factoriesCache.computeIfAbsent(
         resourceLocation, key -> new SpringFactoriesLoader.Factories(loadFactoriesResource(resourceClassLoader, resourceLocation))
      );
      return new SpringFactoriesLoader(classLoader, factories.byType());
   }

   protected static Map<String, List<String>> loadFactoriesResource(ClassLoader classLoader, String resourceLocation) {
      Map<String, List<String>> result = new LinkedHashMap<>();

      try {
         Enumeration<URL> urls = classLoader.getResources(resourceLocation);

         while (urls.hasMoreElements()) {
            UrlResource resource = new UrlResource(urls.nextElement());
            Properties properties = PropertiesLoaderUtils.loadProperties(resource);
            properties.forEach((name, value) -> {
               String[] factoryImplementationNames = StringUtils.commaDelimitedListToStringArray((String)value);
               List<String> implementations = result.computeIfAbsent(((String)name).trim(), key -> new ArrayList<>(factoryImplementationNames.length));
               Arrays.stream(factoryImplementationNames).map(String::trim).forEach(implementations::add);
            });
         }

         result.replaceAll(SpringFactoriesLoader::toDistinctUnmodifiableList);
      } catch (IOException ex) {
         throw new IllegalArgumentException("Unable to load factories from location [" + resourceLocation + "]", ex);
      }

      return Collections.unmodifiableMap(result);
   }

   private static List<String> toDistinctUnmodifiableList(String factoryType, List<String> implementations) {
      return implementations.stream().distinct().toList();
   }

   @FunctionalInterface
   public interface ArgumentResolver {
      <T> @Nullable T resolve(Class<T> type);

      default <T> SpringFactoriesLoader.ArgumentResolver and(Class<T> type, T value) {
         return this.and(of(type, value));
      }

      default <T> SpringFactoriesLoader.ArgumentResolver andSupplied(Class<T> type, Supplier<T> valueSupplier) {
         return this.and(ofSupplied(type, valueSupplier));
      }

      default SpringFactoriesLoader.ArgumentResolver and(SpringFactoriesLoader.ArgumentResolver argumentResolver) {
         return from(type -> {
            Object resolved = this.resolve(type);
            return resolved != null ? resolved : argumentResolver.resolve(type);
         });
      }

      static SpringFactoriesLoader.ArgumentResolver none() {
         return from(type -> null);
      }

      static <T> SpringFactoriesLoader.ArgumentResolver of(Class<T> type, T value) {
         return ofSupplied(type, () -> value);
      }

      static <T> SpringFactoriesLoader.ArgumentResolver ofSupplied(Class<T> type, Supplier<T> valueSupplier) {
         return from(candidateType -> candidateType.equals(type) ? valueSupplier.get() : null);
      }

      static SpringFactoriesLoader.ArgumentResolver from(Function<Class<?>, @Nullable Object> function) {
         return new SpringFactoriesLoader.ArgumentResolver() {
            @Override
            public <T> @Nullable T resolve(Class<T> type) {
               return (T)function.apply(type);
            }
         };
      }
   }

   private record Factories(Map<String, List<String>> byType) {
   }

   static final class FactoryInstantiator<T> {
      private static final boolean KOTLIN_REFLECT_PRESENT = KotlinDetector.isKotlinReflectPresent();
      private final Constructor<T> constructor;

      private FactoryInstantiator(Constructor<T> constructor) {
         ReflectionUtils.makeAccessible(constructor);
         this.constructor = constructor;
      }

      T instantiate(SpringFactoriesLoader.@Nullable ArgumentResolver argumentResolver) throws Exception {
         Object[] args = this.resolveArgs(argumentResolver);
         return KOTLIN_REFLECT_PRESENT && KotlinDetector.isKotlinType(this.constructor.getDeclaringClass())
            ? SpringFactoriesLoader.KotlinDelegate.instantiate(this.constructor, args)
            : this.constructor.newInstance(args);
      }

      private Object[] resolveArgs(SpringFactoriesLoader.@Nullable ArgumentResolver argumentResolver) {
         Class<?>[] types = this.constructor.getParameterTypes();
         return argumentResolver != null ? Arrays.stream(types).map(argumentResolver::resolve).toArray() : new Object[types.length];
      }

      static <T> SpringFactoriesLoader.FactoryInstantiator<T> forClass(Class<?> factoryImplementationClass) {
         Constructor<?> constructor = findConstructor(factoryImplementationClass);
         Assert.state(constructor != null, () -> "Class [%s] has no suitable constructor".formatted(factoryImplementationClass.getName()));
         return new SpringFactoriesLoader.FactoryInstantiator<>((Constructor<T>)constructor);
      }

      private static @Nullable Constructor<?> findConstructor(Class<?> factoryImplementationClass) {
         Constructor<?> constructor = findPrimaryKotlinConstructor(factoryImplementationClass);
         constructor = constructor != null ? constructor : findSingleConstructor(factoryImplementationClass.getConstructors());
         constructor = constructor != null ? constructor : findSingleConstructor(factoryImplementationClass.getDeclaredConstructors());
         return constructor != null ? constructor : findDeclaredConstructor(factoryImplementationClass);
      }

      private static @Nullable Constructor<?> findPrimaryKotlinConstructor(Class<?> factoryImplementationClass) {
         return KotlinDetector.isKotlinType(factoryImplementationClass)
            ? SpringFactoriesLoader.KotlinDelegate.findPrimaryConstructor(factoryImplementationClass)
            : null;
      }

      private static @Nullable Constructor<?> findSingleConstructor(Constructor<?>[] constructors) {
         return constructors.length == 1 ? constructors[0] : null;
      }

      private static @Nullable Constructor<?> findDeclaredConstructor(Class<?> factoryImplementationClass) {
         try {
            return factoryImplementationClass.getDeclaredConstructor();
         } catch (NoSuchMethodException ex) {
            return null;
         }
      }
   }

   @FunctionalInterface
   public interface FailureHandler {
      void handleFailure(Class<?> factoryType, String factoryImplementationName, Throwable failure);

      static SpringFactoriesLoader.FailureHandler throwing() {
         return throwing(IllegalArgumentException::new);
      }

      static SpringFactoriesLoader.FailureHandler throwing(BiFunction<String, Throwable, ? extends RuntimeException> exceptionFactory) {
         return handleMessage((messageSupplier, failure) -> {
            throw (RuntimeException)exceptionFactory.apply(messageSupplier.get(), failure);
         });
      }

      static SpringFactoriesLoader.FailureHandler logging(Log logger) {
         return handleMessage((messageSupplier, failure) -> logger.trace(LogMessage.of(messageSupplier), failure));
      }

      static SpringFactoriesLoader.FailureHandler handleMessage(BiConsumer<Supplier<String>, Throwable> messageHandler) {
         return (factoryType, factoryImplementationName, failure) -> {
            Supplier<String> messageSupplier = () -> "Unable to instantiate factory class [%s] for factory type [%s]"
               .formatted(factoryImplementationName, factoryType.getName());
            messageHandler.accept(messageSupplier, failure);
         };
      }
   }

   private static class KotlinDelegate {
      static <T> @Nullable Constructor<T> findPrimaryConstructor(Class<T> clazz) {
         try {
            KFunction<T> primaryConstructor = KClasses.getPrimaryConstructor(JvmClassMappingKt.getKotlinClass(clazz));
            if (primaryConstructor != null) {
               Constructor<T> constructor = ReflectJvmMapping.getJavaConstructor(primaryConstructor);
               Assert.state(constructor != null, () -> "Failed to find Java constructor for Kotlin primary constructor: " + clazz.getName());
               return constructor;
            }
         } catch (UnsupportedOperationException var3) {
         }

         return null;
      }

      static <T> T instantiate(Constructor<T> constructor, Object[] args) throws Exception {
         KFunction<T> kotlinConstructor = ReflectJvmMapping.getKotlinFunction(constructor);
         if (kotlinConstructor == null) {
            return constructor.newInstance(args);
         }

         makeAccessible(constructor, kotlinConstructor);
         return instantiate(kotlinConstructor, convertArgs(args, kotlinConstructor.getParameters()));
      }

      private static <T> void makeAccessible(Constructor<T> constructor, KFunction<T> kotlinConstructor) {
         if (!Modifier.isPublic(constructor.getModifiers()) || !Modifier.isPublic(constructor.getDeclaringClass().getModifiers())) {
            KCallablesJvm.setAccessible(kotlinConstructor, true);
         }
      }

      private static Map<KParameter, Object> convertArgs(Object[] args, List<KParameter> parameters) {
         Map<KParameter, Object> result = CollectionUtils.newHashMap(parameters.size());
         Assert.isTrue(args.length <= parameters.size(), "Number of provided arguments should be less than or equal to the number of constructor parameters");

         for (int i = 0; i < args.length; i++) {
            if (!parameters.get(i).isOptional() || args[i] != null) {
               result.put(parameters.get(i), args[i]);
            }
         }

         return result;
      }

      private static <T> T instantiate(KFunction<T> kotlinConstructor, Map<KParameter, Object> args) {
         return (T)kotlinConstructor.callBy(args);
      }
   }
}
