package org.springframework.boot.context.properties.bind;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.CollectionFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.log.LogMessage;
import org.springframework.util.Assert;

class ValueObjectBinder implements DataObjectBinder {
   private static final Log logger = LogFactory.getLog(ValueObjectBinder.class);
   private final BindConstructorProvider constructorProvider;

   ValueObjectBinder(BindConstructorProvider constructorProvider) {
      this.constructorProvider = constructorProvider;
   }

   @Override
   public <T> T bind(
      ConfigurationPropertyName name, Bindable<T> target, Binder.Context context, DataObjectPropertyBinder propertyBinder, boolean fallbackToDefaultValue
   ) {
      ValueObjectBinder.ValueObject<T> valueObject = ValueObjectBinder.ValueObject.get(
         target, context, this.constructorProvider, ValueObjectBinder.Discoverer.LENIENT
      );
      if (valueObject != null) {
         Class<?> targetType = target.getType().resolve();
         Assert.state(targetType != null, "'targetType' must not be null");
         context.pushConstructorBoundTypes(targetType);
         List<ValueObjectBinder.ConstructorParameter> parameters = valueObject.getConstructorParameters();
         List<Object> args = new ArrayList<>(parameters.size());
         boolean bound = false;

         for (ValueObjectBinder.ConstructorParameter parameter : parameters) {
            Object arg = parameter.bind(propertyBinder);
            bound = bound || arg != null;
            arg = arg != null ? arg : this.getDefaultValue(context, parameter);
            args.add(arg);
         }

         context.clearConfigurationProperty();
         context.popConstructorBoundTypes();
         if (bound) {
            return valueObject.instantiate(args);
         }
      }

      return fallbackToDefaultValue ? this.getNewDefaultValueInstanceIfPossible(context, target.getType()) : null;
   }

   @Override
   public <T> T create(Bindable<T> target, Binder.Context context) {
      ValueObjectBinder.ValueObject<T> valueObject = ValueObjectBinder.ValueObject.get(
         target, context, this.constructorProvider, ValueObjectBinder.Discoverer.LENIENT
      );
      if (valueObject == null) {
         return null;
      }

      List<ValueObjectBinder.ConstructorParameter> parameters = valueObject.getConstructorParameters();
      List<Object> args = new ArrayList<>(parameters.size());

      for (ValueObjectBinder.ConstructorParameter parameter : parameters) {
         args.add(this.getDefaultValue(context, parameter));
      }

      return valueObject.instantiate(args);
   }

   @Override
   public <T> void onUnableToCreateInstance(Bindable<T> target, Binder.Context context, RuntimeException exception) {
      try {
         ValueObjectBinder.ValueObject.get(target, context, this.constructorProvider, ValueObjectBinder.Discoverer.STRICT);
      } catch (Exception ex) {
         exception.addSuppressed(ex);
      }
   }

   private <T> @Nullable T getDefaultValue(Binder.Context context, ValueObjectBinder.ConstructorParameter parameter) {
      ResolvableType type = parameter.getType();
      Annotation[] annotations = parameter.getAnnotations();

      for (Annotation annotation : annotations) {
         if (annotation instanceof DefaultValue defaultValueAnnotation) {
            String[] defaultValue = defaultValueAnnotation.value();
            if (defaultValue.length == 0) {
               return this.getNewDefaultValueInstanceIfPossible(context, type);
            }

            return this.convertDefaultValue(
               context.getConverter(), this.resolveDefaultValue(context.getPlaceholdersResolver(), defaultValue), type, annotations
            );
         }
      }

      return context.getConverter().convert(null, type);
   }

   private String[] resolveDefaultValue(PlaceholdersResolver resolver, String[] defaultValue) {
      String[] resolved = new String[defaultValue.length];

      for (int i = 0; i < defaultValue.length; i++) {
         Object value = resolver.resolvePlaceholders(defaultValue[i]);
         resolved[i] = String.valueOf(value);
      }

      return resolved;
   }

   private <T> @Nullable T convertDefaultValue(BindConverter converter, String[] defaultValue, ResolvableType type, Annotation[] annotations) {
      try {
         return converter.convert(defaultValue, type, annotations);
      } catch (ConversionException ex) {
         if (defaultValue.length == 1) {
            return converter.convert(defaultValue[0], type, annotations);
         } else {
            throw ex;
         }
      }
   }

   private <T> @Nullable T getNewDefaultValueInstanceIfPossible(Binder.Context context, ResolvableType type) {
      Class<T> resolved = type.resolve();
      Assert.state(resolved == null || this.isEmptyDefaultValueAllowed(resolved), () -> "Parameter of type " + type + " must have a non-empty default value.");
      if (resolved != null) {
         if (Optional.class == resolved) {
            return (T)Optional.empty();
         }

         if (Collection.class.isAssignableFrom(resolved)) {
            return (T)CollectionFactory.createCollection(resolved, 0);
         }

         if (EnumMap.class.isAssignableFrom(resolved)) {
            Class<?> keyType = type.asMap().resolveGeneric(new int[]{0});
            return (T)CollectionFactory.createMap(resolved, keyType, 0);
         }

         if (Map.class.isAssignableFrom(resolved)) {
            return (T)CollectionFactory.createMap(resolved, 0);
         }

         if (resolved.isArray()) {
            return (T)Array.newInstance(resolved.getComponentType(), 0);
         }
      }

      T instance = this.create(Bindable.of(type), context);
      if (instance != null) {
         return instance;
      } else {
         return (T)(resolved != null ? BeanUtils.instantiateClass(resolved) : null);
      }
   }

   private boolean isEmptyDefaultValueAllowed(Class<?> type) {
      return Optional.class == type || this.isAggregate(type) || !type.isPrimitive() && !type.isEnum() && !type.getName().startsWith("java.lang");
   }

   private boolean isAggregate(Class<?> type) {
      return type.isArray() || Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type);
   }

   private static class ConstructorParameter {
      private final String name;
      private final ResolvableType type;
      private final Annotation[] annotations;

      ConstructorParameter(String name, ResolvableType type, Annotation[] annotations) {
         this.name = DataObjectPropertyName.toDashedForm(name);
         this.type = type;
         this.annotations = annotations;
      }

      @Nullable Object bind(DataObjectPropertyBinder propertyBinder) {
         return propertyBinder.bindProperty(this.name, Bindable.of(this.type).withAnnotations(this.annotations));
      }

      Annotation[] getAnnotations() {
         return this.annotations;
      }

      ResolvableType getType() {
         return this.type;
      }
   }

   private static final class DefaultValueObject<T> extends ValueObjectBinder.ValueObject<T> {
      private final List<ValueObjectBinder.ConstructorParameter> constructorParameters;

      private DefaultValueObject(Constructor<T> constructor, List<ValueObjectBinder.ConstructorParameter> constructorParameters) {
         super(constructor);
         this.constructorParameters = constructorParameters;
      }

      @Override
      List<ValueObjectBinder.ConstructorParameter> getConstructorParameters() {
         return this.constructorParameters;
      }

      static <T> ValueObjectBinder.ValueObject<T> get(Constructor<?> bindConstructor, ResolvableType type, ParameterNameDiscoverer parameterNameDiscoverer) {
         String[] names = parameterNameDiscoverer.getParameterNames(bindConstructor);
         if (names == null) {
            return null;
         }

         List<ValueObjectBinder.ConstructorParameter> constructorParameters = parseConstructorParameters(bindConstructor, type, names);
         return new ValueObjectBinder.DefaultValueObject<>((Constructor<T>)bindConstructor, constructorParameters);
      }

      private static List<ValueObjectBinder.ConstructorParameter> parseConstructorParameters(
         Constructor<?> constructor, ResolvableType type, @Nullable String[] names
      ) {
         Parameter[] parameters = constructor.getParameters();
         List<ValueObjectBinder.ConstructorParameter> result = new ArrayList<>(parameters.length);

         for (int i = 0; i < parameters.length; i++) {
            String name = MergedAnnotations.from(parameters[i]).get(Name.class).getValue("value", String.class).orElse(names[i]);
            ResolvableType parameterType = ResolvableType.forMethodParameter(new MethodParameter(constructor, i), type);
            Annotation[] annotations = parameters[i].getDeclaredAnnotations();
            Assert.state(name != null, "'name' must not be null");
            result.add(new ValueObjectBinder.ConstructorParameter(name, parameterType, annotations));
         }

         return Collections.unmodifiableList(result);
      }
   }

   static final class Discoverer implements ParameterNameDiscoverer {
      private static final ParameterNameDiscoverer DEFAULT_DELEGATE = new DefaultParameterNameDiscoverer();
      private static final ParameterNameDiscoverer LENIENT = new ValueObjectBinder.Discoverer(DEFAULT_DELEGATE, message -> {});
      private static final ParameterNameDiscoverer STRICT = new ValueObjectBinder.Discoverer(DEFAULT_DELEGATE, message -> {
         throw new IllegalStateException(message.toString());
      });
      private final ParameterNameDiscoverer delegate;
      private final Consumer<LogMessage> noParameterNamesHandler;

      private Discoverer(ParameterNameDiscoverer delegate, Consumer<LogMessage> noParameterNamesHandler) {
         this.delegate = delegate;
         this.noParameterNamesHandler = noParameterNamesHandler;
      }

      public String[] getParameterNames(Method method) {
         throw new UnsupportedOperationException();
      }

      public String[] getParameterNames(Constructor<?> constructor) {
         String[] names = this.delegate.getParameterNames(constructor);
         if (names != null) {
            return names;
         }

         LogMessage message = LogMessage.format(
            "Unable to use value object binding with constructor [%s] as parameter names cannot be discovered. Ensure that the compiler uses the '-parameters' flag",
            constructor
         );
         this.noParameterNamesHandler.accept(message);
         ValueObjectBinder.logger.debug(message);
         return null;
      }
   }

   private static final class KotlinValueObject<T> extends ValueObjectBinder.ValueObject<T> {
      private static final Annotation[] ANNOTATION_ARRAY = new Annotation[0];
      private final List<ValueObjectBinder.ConstructorParameter> constructorParameters;

      private KotlinValueObject(Constructor<T> primaryConstructor, KFunction<T> kotlinConstructor, ResolvableType type) {
         super(primaryConstructor);
         this.constructorParameters = this.parseConstructorParameters(kotlinConstructor, type);
      }

      private List<ValueObjectBinder.ConstructorParameter> parseConstructorParameters(KFunction<T> kotlinConstructor, ResolvableType type) {
         List<KParameter> parameters = kotlinConstructor.getParameters();
         List<ValueObjectBinder.ConstructorParameter> result = new ArrayList<>(parameters.size());

         for (KParameter parameter : parameters) {
            String name = this.getParameterName(parameter);
            ResolvableType parameterType = ResolvableType.forType(ReflectJvmMapping.getJavaType(parameter.getType()), type);
            Annotation[] annotations = parameter.getAnnotations().toArray(ANNOTATION_ARRAY);
            Assert.state(name != null, "'name' must not be null");
            result.add(new ValueObjectBinder.ConstructorParameter(name, parameterType, annotations));
         }

         return Collections.unmodifiableList(result);
      }

      private @Nullable String getParameterName(KParameter parameter) {
         return (String)MergedAnnotations.from(parameter, parameter.getAnnotations().toArray(ANNOTATION_ARRAY))
            .get(Name.class)
            .getValue("value", String.class)
            .orElseGet(parameter::getName);
      }

      @Override
      List<ValueObjectBinder.ConstructorParameter> getConstructorParameters() {
         return this.constructorParameters;
      }

      static <T> ValueObjectBinder.@Nullable ValueObject<T> get(
         Constructor<T> bindConstructor, ResolvableType type, ParameterNameDiscoverer parameterNameDiscoverer
      ) {
         KFunction<T> kotlinConstructor = ReflectJvmMapping.getKotlinFunction(bindConstructor);
         return kotlinConstructor != null
            ? new ValueObjectBinder.KotlinValueObject<>(bindConstructor, kotlinConstructor, type)
            : ValueObjectBinder.DefaultValueObject.get(bindConstructor, type, parameterNameDiscoverer);
      }
   }

   private abstract static class ValueObject<T> {
      private static final Object NONE = new Object();
      private final Constructor<T> constructor;

      protected ValueObject(Constructor<T> constructor) {
         this.constructor = constructor;
      }

      T instantiate(List<@Nullable Object> args) {
         return (T)BeanUtils.instantiateClass(this.constructor, args.toArray());
      }

      abstract List<ValueObjectBinder.ConstructorParameter> getConstructorParameters();

      static <T> ValueObjectBinder.@Nullable ValueObject<T> get(
         Bindable<T> bindable, Binder.Context context, BindConstructorProvider constructorProvider, ParameterNameDiscoverer parameterNameDiscoverer
      ) {
         Class<T> resolvedType = bindable.getType().resolve();
         if (resolvedType != null && !resolvedType.isEnum() && !Modifier.isAbstract(resolvedType.getModifiers())) {
            Map<ValueObjectBinder.ValueObject.CacheKey, Object> cache = getCache(context);
            ValueObjectBinder.ValueObject.CacheKey cacheKey = new ValueObjectBinder.ValueObject.CacheKey(bindable, constructorProvider, parameterNameDiscoverer);
            Object valueObject = cache.get(cacheKey);
            if (valueObject == null) {
               valueObject = get(bindable, context, constructorProvider, parameterNameDiscoverer, resolvedType);
               cache.put(cacheKey, valueObject != null ? valueObject : NONE);
            }

            return valueObject != NONE ? (ValueObjectBinder.ValueObject)valueObject : null;
         } else {
            return null;
         }
      }

      private static <T> ValueObjectBinder.@Nullable ValueObject<T> get(
         Bindable<T> bindable,
         Binder.Context context,
         BindConstructorProvider constructorProvider,
         ParameterNameDiscoverer parameterNameDiscoverer,
         Class<T> resolvedType
      ) {
         Constructor<?> bindConstructor = constructorProvider.getBindConstructor(bindable, context.isNestedConstructorBinding());
         if (bindConstructor == null) {
            return null;
         } else {
            return KotlinDetector.isKotlinType(resolvedType)
               ? ValueObjectBinder.KotlinValueObject.get((Constructor<T>)bindConstructor, bindable.getType(), parameterNameDiscoverer)
               : ValueObjectBinder.DefaultValueObject.get(bindConstructor, bindable.getType(), parameterNameDiscoverer);
         }
      }

      private static Map<ValueObjectBinder.ValueObject.CacheKey, Object> getCache(Binder.Context context) {
         Map<ValueObjectBinder.ValueObject.CacheKey, Object> cache = (Map<ValueObjectBinder.ValueObject.CacheKey, Object>)context.getCache()
            .get(ValueObjectBinder.ValueObject.class);
         if (cache == null) {
            cache = new ConcurrentHashMap<>();
            context.getCache().put(ValueObjectBinder.ValueObject.class, cache);
         }

         return cache;
      }

      private record CacheKey(Bindable<?> bindable, BindConstructorProvider constructorProvider, ParameterNameDiscoverer parameterNameDiscoverer) {
      }
   }
}
