package org.springframework.boot.context.properties.bind;

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;

class JavaBeanBinder implements DataObjectBinder {
   private static final String HAS_KNOWN_BINDABLE_PROPERTIES_CACHE = JavaBeanBinder.class.getName() + ".HAS_KNOWN_BINDABLE_PROPERTIES_CACHE";
   static final JavaBeanBinder INSTANCE = new JavaBeanBinder();

   @Override
   public <T> @Nullable T bind(
      ConfigurationPropertyName name, Bindable<T> target, Binder.Context context, DataObjectPropertyBinder propertyBinder, boolean fallbackToDefaultValue
   ) {
      boolean hasKnownBindableProperties = target.getValue() != null && this.hasKnownBindableProperties(name, context);
      JavaBeanBinder.Bean<T> bean = JavaBeanBinder.Bean.get(target, context, hasKnownBindableProperties);
      if (bean == null) {
         return null;
      }

      JavaBeanBinder.BeanSupplier<T> beanSupplier = bean.getSupplier(target);
      boolean bound = this.bind(propertyBinder, bean, beanSupplier, context);
      return bound ? beanSupplier.get() : null;
   }

   @Override
   public <T> @Nullable T create(Bindable<T> target, Binder.Context context) {
      Class<T> type = target.getType().resolve();
      return (T)(type != null ? BeanUtils.instantiateClass(type) : null);
   }

   private boolean hasKnownBindableProperties(ConfigurationPropertyName name, Binder.Context context) {
      Map<ConfigurationPropertyName, Boolean> cache = this.getHasKnownBindablePropertiesCache(context);
      Boolean hasKnownBindableProperties = cache.get(name);
      if (hasKnownBindableProperties == null) {
         hasKnownBindableProperties = this.computeHasKnownBindableProperties(name, context);
         cache.put(name, hasKnownBindableProperties);
      }

      return hasKnownBindableProperties;
   }

   private boolean computeHasKnownBindableProperties(ConfigurationPropertyName name, Binder.Context context) {
      for (ConfigurationPropertySource source : context.getSources()) {
         if (source.containsDescendantOf(name) == ConfigurationPropertyState.PRESENT) {
            return true;
         }
      }

      return false;
   }

   private Map<ConfigurationPropertyName, Boolean> getHasKnownBindablePropertiesCache(Binder.Context context) {
      Object cache = context.getCache().get(HAS_KNOWN_BINDABLE_PROPERTIES_CACHE);
      if (cache == null) {
         cache = new ConcurrentHashMap();
         context.getCache().put(HAS_KNOWN_BINDABLE_PROPERTIES_CACHE, cache);
      }

      return (Map<ConfigurationPropertyName, Boolean>)cache;
   }

   private <T> boolean bind(
      DataObjectPropertyBinder propertyBinder, JavaBeanBinder.Bean<T> bean, JavaBeanBinder.BeanSupplier<T> beanSupplier, Binder.Context context
   ) {
      boolean bound = false;

      for (JavaBeanBinder.BeanProperty beanProperty : bean.getProperties().values()) {
         bound |= this.bind(beanSupplier, propertyBinder, beanProperty);
         context.clearConfigurationProperty();
      }

      return bound;
   }

   private <T> boolean bind(JavaBeanBinder.BeanSupplier<T> beanSupplier, DataObjectPropertyBinder propertyBinder, JavaBeanBinder.BeanProperty property) {
      String propertyName = this.determinePropertyName(property);
      ResolvableType type = property.getType();
      Supplier<Object> value = property.getValue(beanSupplier);
      Annotation[] annotations = property.getAnnotations();
      Object bound = propertyBinder.bindProperty(propertyName, Bindable.<Object>of(type).withSuppliedValue(value).withAnnotations(annotations));
      if (bound == null) {
         return false;
      }

      if (property.isSettable()) {
         property.setValue(beanSupplier, bound);
      } else if (value == null || !bound.equals(value.get())) {
         throw new IllegalStateException("No setter found for property: " + property.getName());
      }

      return true;
   }

   private String determinePropertyName(JavaBeanBinder.BeanProperty property) {
      return Arrays.stream(property.getAnnotations() != null ? property.getAnnotations() : new Annotation[0])
         .filter(annotation -> annotation.annotationType() == Name.class)
         .findFirst()
         .map(Name.class::cast)
         .map(Name::value)
         .orElse(property.getName());
   }

   static class Bean<T> extends JavaBeanBinder.BeanProperties {
      Bean(ResolvableType type, Class<?> resolvedType) {
         super(type, resolvedType);
      }

      JavaBeanBinder.BeanSupplier<T> getSupplier(Bindable<T> target) {
         return new JavaBeanBinder.BeanSupplier<>(() -> {
            T instance = null;
            if (target.getValue() != null) {
               instance = target.getValue().get();
            }

            if (instance == null) {
               instance = (T)BeanUtils.instantiateClass(this.getResolvedType());
            }

            return instance;
         });
      }

      static <T> JavaBeanBinder.@Nullable Bean<T> get(Bindable<T> bindable, Binder.Context context, boolean canCallGetValue) {
         ResolvableType type = bindable.getType();
         Class<?> resolvedType = type.resolve(Object.class);
         Supplier<T> value = bindable.getValue();
         T instance = null;
         if (canCallGetValue && value != null) {
            instance = value.get();
            resolvedType = instance != null ? instance.getClass() : resolvedType;
         }

         if (instance == null && !isInstantiable(resolvedType)) {
            return null;
         }

         Map<JavaBeanBinder.Bean.CacheKey, JavaBeanBinder.Bean<?>> cache = getCache(context);
         JavaBeanBinder.Bean.CacheKey cacheKey = new JavaBeanBinder.Bean.CacheKey(type, resolvedType);
         JavaBeanBinder.Bean<?> bean = cache.get(cacheKey);
         if (bean == null) {
            bean = new JavaBeanBinder.Bean(type, resolvedType);
            cache.put(cacheKey, bean);
         }

         return (JavaBeanBinder.Bean<T>)bean;
      }

      private static Map<JavaBeanBinder.Bean.CacheKey, JavaBeanBinder.Bean<?>> getCache(Binder.Context context) {
         Map<JavaBeanBinder.Bean.CacheKey, JavaBeanBinder.Bean<?>> cache = (Map<JavaBeanBinder.Bean.CacheKey, JavaBeanBinder.Bean<?>>)context.getCache()
            .get(JavaBeanBinder.Bean.class);
         if (cache == null) {
            cache = new ConcurrentHashMap<>();
            context.getCache().put(JavaBeanBinder.Bean.class, cache);
         }

         return cache;
      }

      private static boolean isInstantiable(Class<?> type) {
         if (type.isInterface()) {
            return false;
         }

         try {
            type.getDeclaredConstructor();
            return true;
         } catch (Exception ex) {
            return false;
         }
      }

      private record CacheKey(ResolvableType type, Class<?> resolvedType) {
      }
   }

   static class BeanProperties {
      private final Map<String, JavaBeanBinder.BeanProperty> properties = new LinkedHashMap<>();
      private final ResolvableType type;
      private final Class<?> resolvedType;

      BeanProperties(ResolvableType type, Class<?> resolvedType) {
         this.type = type;
         this.resolvedType = resolvedType;
         this.addProperties(resolvedType);
      }

      private void addProperties(Class<?> type) {
         while (type != null && !Object.class.equals(type)) {
            Method[] declaredMethods = this.getSorted(type, this::getDeclaredMethods, Method::getName);
            Field[] declaredFields = this.getSorted(type, Class::getDeclaredFields, Field::getName);
            this.addProperties(declaredMethods, declaredFields);
            type = type.getSuperclass();
         }
      }

      private Method[] getDeclaredMethods(Class<?> type) {
         Method[] methods = type.getDeclaredMethods();
         Set<Method> result = new LinkedHashSet<>(methods.length);

         for (Method method : methods) {
            result.add(BridgeMethodResolver.findBridgedMethod(method));
         }

         return result.toArray(new Method[0]);
      }

      private <S, E> E[] getSorted(S source, Function<S, E[]> elements, Function<E, String> name) {
         E[] result = (E[])elements.apply(source);
         Arrays.sort(result, Comparator.comparing(name));
         return result;
      }

      protected void addProperties(Method[] declaredMethods, Field[] declaredFields) {
         Method[] methods = new Method[declaredMethods.length];

         for (int i = 0; i < declaredMethods.length; i++) {
            methods[i] = this.isCandidate(declaredMethods[i]) ? declaredMethods[i] : null;
         }

         for (Method method : methods) {
            this.addMethodIfPossible(method, "is", 0, JavaBeanBinder.BeanProperty::addGetter);
         }

         for (Method method : methods) {
            this.addMethodIfPossible(method, "get", 0, JavaBeanBinder.BeanProperty::addGetter);
         }

         for (Method method : methods) {
            this.addMethodIfPossible(method, "set", 1, JavaBeanBinder.BeanProperty::addSetter);
         }

         for (Field field : declaredFields) {
            this.addField(field);
         }
      }

      private boolean isCandidate(Method method) {
         int modifiers = method.getModifiers();
         return !Modifier.isPrivate(modifiers)
            && !Modifier.isProtected(modifiers)
            && !Modifier.isAbstract(modifiers)
            && !Modifier.isStatic(modifiers)
            && !method.isBridge()
            && !Object.class.equals(method.getDeclaringClass())
            && !Class.class.equals(method.getDeclaringClass())
            && method.getName().indexOf(36) == -1;
      }

      private void addMethodIfPossible(@Nullable Method method, String prefix, int parameterCount, BiConsumer<JavaBeanBinder.BeanProperty, Method> consumer) {
         if (method != null
            && method.getParameterCount() == parameterCount
            && method.getName().startsWith(prefix)
            && method.getName().length() > prefix.length()) {
            String propertyName = Introspector.decapitalize(method.getName().substring(prefix.length()));
            consumer.accept(this.properties.computeIfAbsent(propertyName, this::getBeanProperty), method);
         }
      }

      private JavaBeanBinder.BeanProperty getBeanProperty(String name) {
         return new JavaBeanBinder.BeanProperty(name, this.type);
      }

      private void addField(Field field) {
         JavaBeanBinder.BeanProperty property = this.properties.get(field.getName());
         if (property != null) {
            property.addField(field);
         }
      }

      protected final ResolvableType getType() {
         return this.type;
      }

      protected final Class<?> getResolvedType() {
         return this.resolvedType;
      }

      final Map<String, JavaBeanBinder.BeanProperty> getProperties() {
         return this.properties;
      }

      static JavaBeanBinder.BeanProperties of(Bindable<?> bindable) {
         ResolvableType type = bindable.getType();
         Class<?> resolvedType = type.resolve(Object.class);
         return new JavaBeanBinder.BeanProperties(type, resolvedType);
      }
   }

   static class BeanProperty {
      private final String name;
      private final ResolvableType declaringClassType;
      private @Nullable Method getter;
      private @Nullable Method setter;
      private @Nullable Field field;

      BeanProperty(String name, ResolvableType declaringClassType) {
         this.name = DataObjectPropertyName.toDashedForm(name);
         this.declaringClassType = declaringClassType;
      }

      void addGetter(Method getter) {
         if (this.getter == null || this.getter.getName().startsWith("is")) {
            this.getter = getter;
         }
      }

      void addSetter(Method setter) {
         if (this.setter == null || this.isBetterSetter(setter)) {
            this.setter = setter;
         }
      }

      private boolean isBetterSetter(Method setter) {
         return this.getter != null && this.getter.getReturnType().equals(setter.getParameterTypes()[0]);
      }

      void addField(Field field) {
         if (this.field == null) {
            this.field = field;
         }
      }

      String getName() {
         return this.name;
      }

      ResolvableType getType() {
         if (this.setter != null) {
            MethodParameter methodParameter = new MethodParameter(this.setter, 0);
            return ResolvableType.forMethodParameter(methodParameter, this.declaringClassType);
         } else {
            Assert.state(this.getter != null, "'getter' must not be null");
            MethodParameter methodParameter = new MethodParameter(this.getter, -1);
            return ResolvableType.forMethodParameter(methodParameter, this.declaringClassType);
         }
      }

      Annotation @Nullable [] getAnnotations() {
         try {
            return this.field != null ? this.field.getDeclaredAnnotations() : null;
         } catch (Exception ex) {
            return null;
         }
      }

      @Nullable Supplier<Object> getValue(Supplier<?> instance) {
         return this.getter == null ? null : () -> {
            Assert.state(this.getter != null, "'getter' must not be null");

            try {
               this.getter.setAccessible(true);
               return this.getter.invoke(instance.get());
            } catch (Exception ex) {
               if (this.isUninitializedKotlinProperty(ex)) {
                  return null;
               } else {
                  throw new IllegalStateException("Unable to get value for property " + this.name, ex);
               }
            }
         };
      }

      private boolean isUninitializedKotlinProperty(Exception ex) {
         return ex instanceof InvocationTargetException invocationTargetException
            && "kotlin.UninitializedPropertyAccessException".equals(invocationTargetException.getTargetException().getClass().getName());
      }

      boolean isSettable() {
         return this.setter != null;
      }

      void setValue(Supplier<?> instance, Object value) {
         Assert.state(this.setter != null, "'setter' must not be null");

         try {
            this.setter.setAccessible(true);
            this.setter.invoke(instance.get(), value);
         } catch (Exception ex) {
            throw new IllegalStateException("Unable to set value for property " + this.name, ex);
         }
      }

      @Nullable Method getGetter() {
         return this.getter;
      }

      @Nullable Method getSetter() {
         return this.setter;
      }

      @Nullable Field getField() {
         return this.field;
      }
   }

   private static class BeanSupplier<T> implements Supplier<T> {
      private final Supplier<T> factory;
      private @Nullable T instance;

      BeanSupplier(Supplier<T> factory) {
         this.factory = factory;
      }

      @Override
      public T get() {
         if (this.instance == null) {
            this.instance = this.factory.get();
         }

         return this.instance;
      }
   }
}
