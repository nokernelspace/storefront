package org.springframework.boot.util;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

public class Instantiator<T> {
   private static final Comparator<Constructor<?>> CONSTRUCTOR_COMPARATOR = Comparator.comparingInt(Constructor::getParameterCount).reversed();
   private static final Instantiator.FailureHandler throwingFailureHandler = (type, implementationName, failure) -> {
      throw new IllegalArgumentException("Unable to instantiate " + implementationName + " [" + type.getName() + "]", failure);
   };
   private final Class<?> type;
   private final Map<Class<?>, Function<Class<?>, @Nullable Object>> availableParameters;
   private final Instantiator.FailureHandler failureHandler;

   public Instantiator(Class<?> type, Consumer<Instantiator.AvailableParameters> availableParameters) {
      this(type, availableParameters, throwingFailureHandler);
   }

   public Instantiator(Class<?> type, Consumer<Instantiator.AvailableParameters> availableParameters, Instantiator.FailureHandler failureHandler) {
      this.type = type;
      this.availableParameters = this.getAvailableParameters(availableParameters);
      this.failureHandler = failureHandler;
   }

   private Map<Class<?>, Function<Class<?>, Object>> getAvailableParameters(Consumer<Instantiator.AvailableParameters> availableParameters) {
      final Map<Class<?>, Function<Class<?>, Object>> result = new LinkedHashMap<>();
      availableParameters.accept(new Instantiator.AvailableParameters() {
         @Override
         public void add(Class<?> type, @Nullable Object instance) {
            result.put(type, factoryType -> instance);
         }

         @Override
         public void add(Class<?> type, Function<Class<?>, @Nullable Object> factory) {
            result.put(type, factory);
         }
      });
      return Collections.unmodifiableMap(result);
   }

   public List<T> instantiate(Collection<String> names) {
      return this.instantiate(null, names);
   }

   public List<T> instantiate(@Nullable ClassLoader classLoader, Collection<String> names) {
      Assert.notNull(names, "'names' must not be null");
      return this.instantiate(names.stream().map(name -> Instantiator.TypeSupplier.forName(classLoader, name)));
   }

   public @Nullable T instantiate(String name) {
      return this.instantiate(null, name);
   }

   public @Nullable T instantiate(@Nullable ClassLoader classLoader, String name) {
      return this.instantiate(Instantiator.TypeSupplier.forName(classLoader, name));
   }

   public @Nullable T instantiateType(Class<?> type) {
      Assert.notNull(type, "'type' must not be null");
      return this.instantiate(Instantiator.TypeSupplier.forType(type));
   }

   public List<T> instantiateTypes(Collection<Class<?>> types) {
      Assert.notNull(types, "'types' must not be null");
      return this.instantiate(types.stream().map(Instantiator.TypeSupplier::forType));
   }

   public <A> @Nullable A getArg(Class<A> type) {
      Assert.notNull(type, "'type' must not be null");
      Function<Class<?>, Object> parameter = this.getAvailableParameter(type);
      Assert.state(parameter != null, "Unknown argument type " + type.getName());
      return (A)parameter.apply(this.type);
   }

   private List<T> instantiate(Stream<Instantiator.TypeSupplier> typeSuppliers) {
      return typeSuppliers.<T>map(this::instantiate).sorted(AnnotationAwareOrderComparator.INSTANCE).toList();
   }

   private @Nullable T instantiate(Instantiator.TypeSupplier typeSupplier) {
      try {
         Class<?> type = typeSupplier.get();
         Assert.state(this.type.isAssignableFrom(type), () -> type + " is not assignable to " + this.type);
         return this.instantiate(type);
      } catch (Throwable ex) {
         this.failureHandler.handleFailure(this.type, typeSupplier.getName(), ex);
         return null;
      }
   }

   private T instantiate(Class<?> type) throws Exception {
      Constructor<?>[] constructors = type.getDeclaredConstructors();
      Arrays.sort(constructors, CONSTRUCTOR_COMPARATOR);

      for (Constructor<?> constructor : constructors) {
         Object[] args = this.getArgs(constructor.getParameterTypes());
         if (args != null) {
            ReflectionUtils.makeAccessible(constructor);
            return (T)constructor.newInstance(args);
         }
      }

      throw new IllegalAccessException("Class [" + type.getName() + "] has no suitable constructor");
   }

   private Object @Nullable [] getArgs(Class<?>[] parameterTypes) {
      Object[] args = new Object[parameterTypes.length];

      for (int i = 0; i < parameterTypes.length; i++) {
         Function<Class<?>, Object> parameter = this.getAvailableParameter(parameterTypes[i]);
         if (parameter == null) {
            return null;
         }

         args[i] = parameter.apply(this.type);
      }

      return args;
   }

   private @Nullable Function<Class<?>, Object> getAvailableParameter(Class<?> parameterType) {
      for (Entry<Class<?>, Function<Class<?>, Object>> entry : this.availableParameters.entrySet()) {
         if (entry.getKey().isAssignableFrom(parameterType)) {
            return entry.getValue();
         }
      }

      return null;
   }

   public interface AvailableParameters {
      void add(Class<?> type, @Nullable Object instance);

      void add(Class<?> type, Function<Class<?>, @Nullable Object> factory);
   }

   public interface FailureHandler {
      void handleFailure(Class<?> type, String implementationName, Throwable failure);
   }

   private interface TypeSupplier {
      String getName();

      Class<?> get() throws ClassNotFoundException;

      static Instantiator.TypeSupplier forName(@Nullable ClassLoader classLoader, String name) {
         return new Instantiator.TypeSupplier() {
            @Override
            public String getName() {
               return name;
            }

            @Override
            public Class<?> get() throws ClassNotFoundException {
               return ClassUtils.forName(name, classLoader);
            }
         };
      }

      static Instantiator.TypeSupplier forType(Class<?> type) {
         return new Instantiator.TypeSupplier() {
            @Override
            public String getName() {
               return type.getName();
            }

            @Override
            public Class<?> get() {
               return type;
            }
         };
      }
   }
}
