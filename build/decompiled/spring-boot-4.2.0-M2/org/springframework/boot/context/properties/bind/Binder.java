package org.springframework.boot.context.properties.bind;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyCaching;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.env.Environment;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;

public class Binder {
   private static final Set<Class<?>> NON_BEAN_CLASSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Object.class, Class.class)));
   private final Iterable<ConfigurationPropertySource> sources;
   private final PlaceholdersResolver placeholdersResolver;
   private final BindConverter bindConverter;
   private final BindHandler defaultBindHandler;
   private final Map<@Nullable BindMethod, List<DataObjectBinder>> dataObjectBinders;
   private final Map<Object, Object> cache = new ConcurrentReferenceHashMap();
   private final ConfigurationPropertyCaching configurationPropertyCaching;

   public Binder(ConfigurationPropertySource... sources) {
      this(sourcesAsList(sources), null, null, null);
   }

   private static List<ConfigurationPropertySource> sourcesAsList(ConfigurationPropertySource[] sources) {
      Assert.notNull(sources, "'sources' must not be null");
      return Arrays.asList(sources);
   }

   public Binder(Iterable<ConfigurationPropertySource> sources) {
      this(sources, null, null, null);
   }

   public Binder(Iterable<ConfigurationPropertySource> sources, @Nullable PlaceholdersResolver placeholdersResolver) {
      this(sources, placeholdersResolver, null, null);
   }

   public Binder(
      Iterable<ConfigurationPropertySource> sources, @Nullable PlaceholdersResolver placeholdersResolver, @Nullable ConversionService conversionService
   ) {
      this(sources, placeholdersResolver, conversionService, null);
   }

   public Binder(
      Iterable<ConfigurationPropertySource> sources,
      @Nullable PlaceholdersResolver placeholdersResolver,
      @Nullable ConversionService conversionService,
      @Nullable Consumer<PropertyEditorRegistry> propertyEditorInitializer
   ) {
      this(sources, placeholdersResolver, conversionService, propertyEditorInitializer, null);
   }

   public Binder(
      Iterable<ConfigurationPropertySource> sources,
      @Nullable PlaceholdersResolver placeholdersResolver,
      @Nullable ConversionService conversionService,
      @Nullable Consumer<PropertyEditorRegistry> propertyEditorInitializer,
      @Nullable BindHandler defaultBindHandler
   ) {
      this(sources, placeholdersResolver, conversionService, propertyEditorInitializer, defaultBindHandler, null);
   }

   public Binder(
      Iterable<ConfigurationPropertySource> sources,
      @Nullable PlaceholdersResolver placeholdersResolver,
      @Nullable ConversionService conversionService,
      @Nullable Consumer<PropertyEditorRegistry> propertyEditorInitializer,
      @Nullable BindHandler defaultBindHandler,
      @Nullable BindConstructorProvider constructorProvider
   ) {
      this(
         sources,
         placeholdersResolver,
         conversionService != null ? Collections.singletonList(conversionService) : (List)null,
         propertyEditorInitializer,
         defaultBindHandler,
         constructorProvider
      );
   }

   public Binder(
      Iterable<ConfigurationPropertySource> sources,
      PlaceholdersResolver placeholdersResolver,
      List<ConversionService> conversionServices,
      Consumer<PropertyEditorRegistry> propertyEditorInitializer,
      BindHandler defaultBindHandler,
      BindConstructorProvider constructorProvider
   ) {
      Assert.notNull(sources, "'sources' must not be null");

      for (ConfigurationPropertySource source : sources) {
         Assert.notNull(source, "'sources' must not contain null elements");
      }

      this.sources = sources;
      this.configurationPropertyCaching = ConfigurationPropertyCaching.get(sources);
      this.placeholdersResolver = placeholdersResolver != null ? placeholdersResolver : PlaceholdersResolver.NONE;
      this.bindConverter = BindConverter.get(conversionServices, propertyEditorInitializer);
      this.defaultBindHandler = defaultBindHandler != null ? defaultBindHandler : BindHandler.DEFAULT;
      if (constructorProvider == null) {
         constructorProvider = BindConstructorProvider.DEFAULT;
      }

      ValueObjectBinder valueObjectBinder = new ValueObjectBinder(constructorProvider);
      JavaBeanBinder javaBeanBinder = JavaBeanBinder.INSTANCE;
      Map<BindMethod, List<DataObjectBinder>> dataObjectBinders = new HashMap<>();
      dataObjectBinders.put(BindMethod.VALUE_OBJECT, List.of(valueObjectBinder));
      dataObjectBinders.put(BindMethod.JAVA_BEAN, List.of(javaBeanBinder));
      dataObjectBinders.put(null, List.of(valueObjectBinder, javaBeanBinder));
      this.dataObjectBinders = Collections.unmodifiableMap(dataObjectBinders);
   }

   public <T> BindResult<T> bind(String name, Class<T> target) {
      return this.bind(name, Bindable.of(target));
   }

   public <T> BindResult<T> bind(String name, Bindable<T> target) {
      return this.bind(ConfigurationPropertyName.of(name), target, null);
   }

   public <T> BindResult<T> bind(ConfigurationPropertyName name, Bindable<T> target) {
      return this.bind(name, target, null);
   }

   public <T> BindResult<T> bind(String name, Bindable<T> target, @Nullable BindHandler handler) {
      return this.bind(ConfigurationPropertyName.of(name), target, handler);
   }

   public <T> BindResult<T> bind(ConfigurationPropertyName name, Bindable<T> target, @Nullable BindHandler handler) {
      T bound = this.bind(name, target, handler, false);
      return BindResult.of(bound);
   }

   public <T> T bindOrCreate(String name, Class<T> target) {
      return this.bindOrCreate(name, Bindable.of(target));
   }

   public <T> T bindOrCreate(String name, Bindable<T> target) {
      return this.bindOrCreate(ConfigurationPropertyName.of(name), target, null);
   }

   public <T> T bindOrCreate(String name, Bindable<T> target, BindHandler handler) {
      return this.bindOrCreate(ConfigurationPropertyName.of(name), target, handler);
   }

   public <T> T bindOrCreate(ConfigurationPropertyName name, Bindable<T> target, @Nullable BindHandler handler) {
      return this.bind(name, target, handler, true);
   }

   @Contract("_, _, _, true -> !null")
   private <T> @Nullable T bind(ConfigurationPropertyName name, Bindable<T> target, @Nullable BindHandler handler, boolean create) {
      Assert.notNull(name, "'name' must not be null");
      Assert.notNull(target, "'target' must not be null");
      handler = handler != null ? handler : this.defaultBindHandler;
      Binder.Context context = new Binder.Context();
      return this.bind(name, target, handler, context, false, create);
   }

   @Contract("_, _, _, _, _, true -> !null")
   private <T> @Nullable T bind(
      ConfigurationPropertyName name, Bindable<T> target, BindHandler handler, Binder.Context context, boolean allowRecursiveBinding, boolean create
   ) {
      try (ConfigurationPropertyCaching.CacheOverride cacheOverride = this.configurationPropertyCaching.override()) {
         try {
            Bindable<T> replacementTarget = handler.onStart(name, target, context);
            if (replacementTarget == null) {
               return this.handleBindResult(name, target, handler, context, null, create);
            }

            target = replacementTarget;
            Object bound = this.bindObject(name, target, handler, context, allowRecursiveBinding);
            return this.handleBindResult(name, target, handler, context, bound, create);
         } catch (Exception ex) {
            return this.handleBindError(name, target, handler, context, ex);
         }
      }
   }

   @Contract("_, _, _, _, _, true -> null")
   private <T> @Nullable T handleBindResult(
      ConfigurationPropertyName name, Bindable<T> target, BindHandler handler, Binder.Context context, @Nullable Object result, boolean create
   ) throws Exception {
      if (result != null) {
         result = handler.onSuccess(name, target, context, result);
         result = context.getConverter().convert(result, target);
      }

      if (result == null && create) {
         result = this.fromDataObjectBinders(target.getBindMethod(), dataObjectBinder -> dataObjectBinder.create(target, context));
         if (result == null) {
            IllegalStateException ex = new IllegalStateException("Unable to create instance for " + target.getType());
            List<DataObjectBinder> dataObjectBinders = this.dataObjectBinders.get(target.getBindMethod());
            Assert.state(dataObjectBinders != null, "'dataObjectBinders' must not be null");
            dataObjectBinders.forEach(dataObjectBinder -> dataObjectBinder.onUnableToCreateInstance(target, context, ex));
            throw ex;
         }

         result = handler.onCreate(name, target, context, result);
         result = context.getConverter().convert(result, target);
      }

      handler.onFinish(name, target, context, result);
      return context.getConverter().convert(result, target);
   }

   private <T> @Nullable T handleBindError(ConfigurationPropertyName name, Bindable<T> target, BindHandler handler, Binder.Context context, Exception error) {
      try {
         Object result = handler.onFailure(name, target, context, error);
         return context.getConverter().convert(result, target);
      } catch (Exception ex) {
         if (ex instanceof BindException bindException) {
            throw bindException;
         } else {
            throw new BindException(name, target, context.getConfigurationProperty(), ex);
         }
      }
   }

   private <T> @Nullable Object bindObject(
      ConfigurationPropertyName name, Bindable<T> target, BindHandler handler, Binder.Context context, boolean allowRecursiveBinding
   ) {
      ConfigurationProperty property = this.findProperty(name, target, context);
      if (property == null && context.depth != 0 && this.containsNoDescendantOf(context.getSources(), name)) {
         return null;
      }

      AggregateBinder<?> aggregateBinder = this.getAggregateBinder(target, context);
      if (aggregateBinder != null) {
         return this.bindAggregate(name, target, handler, context, aggregateBinder);
      }

      if (property != null) {
         try {
            return this.bindProperty(target, context, property);
         } catch (ConverterNotFoundException ex) {
            boolean fallbackToDefaultValue = ObjectUtils.isEmpty(property.getValue());
            Object instance = this.bindDataObject(name, target, handler, context, allowRecursiveBinding, fallbackToDefaultValue);
            if (instance != null) {
               return instance;
            } else {
               throw ex;
            }
         }
      } else {
         return this.bindDataObject(name, target, handler, context, allowRecursiveBinding, false);
      }
   }

   private @Nullable AggregateBinder<?> getAggregateBinder(Bindable<?> target, Binder.Context context) {
      Class<?> resolvedType = target.getType().resolve(Object.class);
      if (Map.class.isAssignableFrom(resolvedType)) {
         return new MapBinder(context);
      } else if (Collection.class.isAssignableFrom(resolvedType)) {
         return new CollectionBinder(context);
      } else {
         return target.getType().isArray() ? new ArrayBinder(context) : null;
      }
   }

   private <T> Object bindAggregate(
      ConfigurationPropertyName name, Bindable<T> target, BindHandler handler, Binder.Context context, AggregateBinder<?> aggregateBinder
   ) {
      AggregateElementBinder elementBinder = (itemName, itemTarget, source) -> {
         boolean allowRecursiveBinding = aggregateBinder.isAllowRecursiveBinding(source);
         Supplier<?> supplierx = () -> this.bind(itemName, itemTarget, handler, context, allowRecursiveBinding, false);
         return context.withSource(source, (Supplier<T>)supplierx);
      };
      Supplier<Object> supplier = () -> aggregateBinder.bind(name, target, elementBinder);
      return context.withIncreasedDepth((Supplier<T>)supplier);
   }

   private <T> @Nullable ConfigurationProperty findProperty(ConfigurationPropertyName name, Bindable<T> target, Binder.Context context) {
      if (!name.isEmpty() && !target.hasBindRestriction(Bindable.BindRestriction.NO_DIRECT_PROPERTY)) {
         for (ConfigurationPropertySource source : context.getSources()) {
            ConfigurationProperty property = source.getConfigurationProperty(name);
            if (property != null) {
               return property;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private <T> @Nullable Object bindProperty(Bindable<T> target, Binder.Context context, ConfigurationProperty property) {
      context.setConfigurationProperty(property);
      Object result = property.getValue();
      result = this.placeholdersResolver.resolvePlaceholders(result);
      return context.getConverter().convert(result, target);
   }

   private Object bindDataObject(
      ConfigurationPropertyName name,
      Bindable<?> target,
      BindHandler handler,
      Binder.Context context,
      boolean allowRecursiveBinding,
      boolean fallbackToDefaultValue
   ) {
      if (this.isUnbindableBean(name, target, context)) {
         return null;
      }

      Class<?> type = target.getType().resolve(Object.class);
      BindMethod bindMethod = target.getBindMethod();
      if (!allowRecursiveBinding && context.isBindingDataObject(type)) {
         return null;
      }

      DataObjectPropertyBinder propertyBinder = (propertyName, propertyTarget) -> this.bind(
         name.append(propertyName), propertyTarget, handler, context, false, false
      );
      Supplier<Object> supplier = () -> this.fromDataObjectBinders(
         bindMethod, dataObjectBinder -> dataObjectBinder.bind(name, target, context, propertyBinder, fallbackToDefaultValue)
      );
      return context.withDataObject(type, supplier);
   }

   private @Nullable Object fromDataObjectBinders(@Nullable BindMethod bindMethod, Function<DataObjectBinder, @Nullable Object> operation) {
      List<DataObjectBinder> dataObjectBinders = this.dataObjectBinders.get(bindMethod);
      Assert.state(dataObjectBinders != null, "'dataObjectBinders' must not be null");

      for (DataObjectBinder dataObjectBinder : dataObjectBinders) {
         Object bound = operation.apply(dataObjectBinder);
         if (bound != null) {
            return bound;
         }
      }

      return null;
   }

   private boolean isUnbindableBean(ConfigurationPropertyName name, Bindable<?> target, Binder.Context context) {
      for (ConfigurationPropertySource source : context.getSources()) {
         if (source.containsDescendantOf(name) == ConfigurationPropertyState.PRESENT) {
            return false;
         }
      }

      Class<?> resolved = target.getType().resolve(Object.class);
      return !resolved.isPrimitive() && !NON_BEAN_CLASSES.contains(resolved) ? resolved.getName().startsWith("java.") : true;
   }

   private boolean containsNoDescendantOf(Iterable<ConfigurationPropertySource> sources, ConfigurationPropertyName name) {
      for (ConfigurationPropertySource source : sources) {
         if (source.containsDescendantOf(name) != ConfigurationPropertyState.ABSENT) {
            return false;
         }
      }

      return true;
   }

   public static Binder get(Environment environment) {
      return get(environment, null);
   }

   public static Binder get(Environment environment, @Nullable BindHandler defaultBindHandler) {
      Iterable<ConfigurationPropertySource> sources = ConfigurationPropertySources.get(environment);
      PropertySourcesPlaceholdersResolver placeholdersResolver = new PropertySourcesPlaceholdersResolver(environment);
      return new Binder(sources, placeholdersResolver, null, null, defaultBindHandler);
   }

   final class Context implements BindContext {
      private int depth;
      private final List<ConfigurationPropertySource> source = Arrays.asList((ConfigurationPropertySource)null);
      private int sourcePushCount;
      private final Deque<Class<?>> dataObjectBindings = new ArrayDeque<>();
      private final Deque<Class<?>> constructorBindings = new ArrayDeque<>();
      private @Nullable ConfigurationProperty configurationProperty;

      private void increaseDepth() {
         this.depth++;
      }

      private void decreaseDepth() {
         this.depth--;
      }

      private <T> T withSource(@Nullable ConfigurationPropertySource source, Supplier<T> supplier) {
         if (source == null) {
            return supplier.get();
         }

         ConfigurationPropertySource previous = this.source.get(0);
         this.source.set(0, source);
         this.sourcePushCount++;

         try {
            return supplier.get();
         } finally {
            this.sourcePushCount--;
            this.source.set(0, previous);
         }
      }

      private <T> @Nullable T withDataObject(Class<?> type, Supplier<@Nullable T> supplier) {
         this.dataObjectBindings.push(type);

         try {
            return this.withIncreasedDepth(supplier);
         } finally {
            this.dataObjectBindings.pop();
         }
      }

      private boolean isBindingDataObject(Class<?> type) {
         return this.dataObjectBindings.contains(type);
      }

      private <T> @Nullable T withIncreasedDepth(Supplier<@Nullable T> supplier) {
         this.increaseDepth();

         try {
            return supplier.get();
         } finally {
            this.decreaseDepth();
         }
      }

      void setConfigurationProperty(ConfigurationProperty configurationProperty) {
         this.configurationProperty = configurationProperty;
      }

      void clearConfigurationProperty() {
         this.configurationProperty = null;
      }

      void pushConstructorBoundTypes(Class<?> value) {
         this.constructorBindings.push(value);
      }

      boolean isNestedConstructorBinding() {
         return !this.constructorBindings.isEmpty();
      }

      void popConstructorBoundTypes() {
         this.constructorBindings.pop();
      }

      PlaceholdersResolver getPlaceholdersResolver() {
         return Binder.this.placeholdersResolver;
      }

      BindConverter getConverter() {
         return Binder.this.bindConverter;
      }

      Map<Object, Object> getCache() {
         return Binder.this.cache;
      }

      @Override
      public Binder getBinder() {
         return Binder.this;
      }

      @Override
      public int getDepth() {
         return this.depth;
      }

      @Override
      public Iterable<ConfigurationPropertySource> getSources() {
         return this.sourcePushCount > 0 ? this.source : Binder.this.sources;
      }

      @Override
      public @Nullable ConfigurationProperty getConfigurationProperty() {
         return this.configurationProperty;
      }
   }
}
