package org.springframework.validation;

import java.beans.PropertyEditor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.InvalidPropertyPathException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessException;
import org.springframework.beans.PropertyBatchUpdateException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyPath;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.PropertyPath.Options;
import org.springframework.core.CollectionFactory;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.format.Formatter;
import org.springframework.format.support.FormatterPropertyEditorAdapter;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.ValidationAnnotationUtils;

public class DataBinder implements PropertyEditorRegistry, TypeConverter {
   public static final String DEFAULT_OBJECT_NAME = "target";
   public static final int DEFAULT_AUTO_GROW_COLLECTION_LIMIT = 256;
   protected static final Log logger = LogFactory.getLog(DataBinder.class);
   private static final int NO_INDEX = -1;
   private @Nullable Object target;
   @Nullable ResolvableType targetType;
   private final String objectName;
   private @Nullable AbstractPropertyBindingResult bindingResult;
   private boolean directFieldAccess = false;
   private DataBinder.@Nullable ExtendedTypeConverter typeConverter;
   private boolean declarativeBinding = false;
   private boolean ignoreUnknownFields = true;
   private boolean ignoreInvalidFields = false;
   private boolean autoGrowNestedPaths = true;
   private int autoGrowCollectionLimit = 256;
   private int maxNestedPathDepth = 100;
   private String @Nullable [] allowedFields;
   private String @Nullable [] disallowedFields;
   private String @Nullable [] requiredFields;
   private DataBinder.@Nullable NameResolver nameResolver;
   private @Nullable ConversionService conversionService;
   private @Nullable MessageCodesResolver messageCodesResolver;
   private BindingErrorProcessor bindingErrorProcessor = new DefaultBindingErrorProcessor();
   private final List<Validator> validators = new ArrayList<>();
   private @Nullable Predicate<Validator> excludedValidators;

   public DataBinder(@Nullable Object target) {
      this(target, "target");
   }

   public DataBinder(@Nullable Object target, String objectName) {
      this.target = ObjectUtils.unwrapOptional(target);
      this.objectName = objectName;
   }

   public @Nullable Object getTarget() {
      return this.target;
   }

   public String getObjectName() {
      return this.objectName;
   }

   public void setTargetType(ResolvableType targetType) {
      Assert.state(this.target == null, "targetType is used to for target creation but target is already set");
      this.targetType = targetType;
   }

   public @Nullable ResolvableType getTargetType() {
      return this.targetType;
   }

   public void setAutoGrowNestedPaths(boolean autoGrowNestedPaths) {
      Assert.state(this.bindingResult == null, "DataBinder is already initialized - call setAutoGrowNestedPaths before other configuration methods");
      this.autoGrowNestedPaths = autoGrowNestedPaths;
   }

   public boolean isAutoGrowNestedPaths() {
      return this.autoGrowNestedPaths;
   }

   public void setAutoGrowCollectionLimit(int autoGrowCollectionLimit) {
      Assert.state(this.bindingResult == null, "DataBinder is already initialized - call setAutoGrowCollectionLimit before other configuration methods");
      this.autoGrowCollectionLimit = autoGrowCollectionLimit;
   }

   public int getAutoGrowCollectionLimit() {
      return this.autoGrowCollectionLimit;
   }

   public void setMaxNestedPathDepth(int maxNestedPathDepth) {
      Assert.state(this.bindingResult == null, "DataBinder is already initialized - call setMaxNestedPathDepth before other configuration methods");
      Assert.isTrue(maxNestedPathDepth >= 0, "'maxNestedPathDepth' must not be negative");
      this.maxNestedPathDepth = maxNestedPathDepth;
   }

   public int getMaxNestedPathDepth() {
      return this.maxNestedPathDepth;
   }

   public void initBeanPropertyAccess() {
      Assert.state(this.bindingResult == null, "DataBinder is already initialized - call initBeanPropertyAccess before other configuration methods");
      this.directFieldAccess = false;
   }

   protected AbstractPropertyBindingResult createBeanPropertyBindingResult() {
      BeanPropertyBindingResult result = new BeanPropertyBindingResult(
         this.getTarget(), this.getObjectName(), this.isAutoGrowNestedPaths(), this.getAutoGrowCollectionLimit(), this.getMaxNestedPathDepth()
      );
      if (this.conversionService != null) {
         result.initConversion(this.conversionService);
      }

      if (this.messageCodesResolver != null) {
         result.setMessageCodesResolver(this.messageCodesResolver);
      }

      return result;
   }

   public void initDirectFieldAccess() {
      Assert.state(this.bindingResult == null, "DataBinder is already initialized - call initDirectFieldAccess before other configuration methods");
      this.directFieldAccess = true;
   }

   protected AbstractPropertyBindingResult createDirectFieldBindingResult() {
      DirectFieldBindingResult result = new DirectFieldBindingResult(
         this.getTarget(), this.getObjectName(), this.isAutoGrowNestedPaths(), this.getAutoGrowCollectionLimit(), this.getMaxNestedPathDepth()
      );
      if (this.conversionService != null) {
         result.initConversion(this.conversionService);
      }

      if (this.messageCodesResolver != null) {
         result.setMessageCodesResolver(this.messageCodesResolver);
      }

      return result;
   }

   protected AbstractPropertyBindingResult getInternalBindingResult() {
      if (this.bindingResult == null) {
         this.bindingResult = this.directFieldAccess ? this.createDirectFieldBindingResult() : this.createBeanPropertyBindingResult();
      }

      return this.bindingResult;
   }

   protected ConfigurablePropertyAccessor getPropertyAccessor() {
      return this.getInternalBindingResult().getPropertyAccessor();
   }

   protected SimpleTypeConverter getSimpleTypeConverter() {
      if (this.typeConverter == null) {
         this.typeConverter = new DataBinder.ExtendedTypeConverter();
         if (this.conversionService != null) {
            this.typeConverter.setConversionService(this.conversionService);
         }
      }

      return this.typeConverter;
   }

   protected PropertyEditorRegistry getPropertyEditorRegistry() {
      return (PropertyEditorRegistry)(this.getTarget() != null ? this.getInternalBindingResult().getPropertyAccessor() : this.getSimpleTypeConverter());
   }

   protected TypeConverter getTypeConverter() {
      return (TypeConverter)(this.getTarget() != null ? this.getInternalBindingResult().getPropertyAccessor() : this.getSimpleTypeConverter());
   }

   public BindingResult getBindingResult() {
      return this.getInternalBindingResult();
   }

   public void setDeclarativeBinding(boolean declarativeBinding) {
      this.declarativeBinding = declarativeBinding;
   }

   public boolean isDeclarativeBinding() {
      return this.declarativeBinding;
   }

   public void setIgnoreUnknownFields(boolean ignoreUnknownFields) {
      this.ignoreUnknownFields = ignoreUnknownFields;
   }

   public boolean isIgnoreUnknownFields() {
      return this.ignoreUnknownFields;
   }

   public void setIgnoreInvalidFields(boolean ignoreInvalidFields) {
      this.ignoreInvalidFields = ignoreInvalidFields;
   }

   public boolean isIgnoreInvalidFields() {
      return this.ignoreInvalidFields;
   }

   public void setAllowedFields(String @Nullable ... allowedFields) {
      this.allowedFields = canonicalPropertyNames(allowedFields);
   }

   public String @Nullable [] getAllowedFields() {
      return this.allowedFields;
   }

   @Deprecated(since = "7.1", forRemoval = true)
   public void setDisallowedFields(String @Nullable ... disallowedFields) {
      this.disallowedFields = canonicalPropertyNames(disallowedFields);
   }

   @Deprecated(since = "7.1", forRemoval = true)
   public String @Nullable [] getDisallowedFields() {
      return this.disallowedFields;
   }

   public void setRequiredFields(String @Nullable ... requiredFields) {
      this.requiredFields = canonicalPropertyNames(requiredFields);
      if (logger.isDebugEnabled()) {
         logger.debug("DataBinder requires binding of required fields [" + StringUtils.arrayToCommaDelimitedString(requiredFields) + "]");
      }
   }

   public String @Nullable [] getRequiredFields() {
      return this.requiredFields;
   }

   public void setNameResolver(DataBinder.NameResolver nameResolver) {
      this.nameResolver = nameResolver;
   }

   public DataBinder.@Nullable NameResolver getNameResolver() {
      return this.nameResolver;
   }

   public void setMessageCodesResolver(@Nullable MessageCodesResolver messageCodesResolver) {
      Assert.state(this.messageCodesResolver == null, "DataBinder is already initialized with MessageCodesResolver");
      this.messageCodesResolver = messageCodesResolver;
      if (this.bindingResult != null && messageCodesResolver != null) {
         this.bindingResult.setMessageCodesResolver(messageCodesResolver);
      }
   }

   public void setBindingErrorProcessor(BindingErrorProcessor bindingErrorProcessor) {
      Assert.notNull(bindingErrorProcessor, "BindingErrorProcessor must not be null");
      this.bindingErrorProcessor = bindingErrorProcessor;
   }

   public BindingErrorProcessor getBindingErrorProcessor() {
      return this.bindingErrorProcessor;
   }

   public void setValidator(@Nullable Validator validator) {
      this.assertValidators(validator);
      this.validators.clear();
      if (validator != null) {
         this.validators.add(validator);
      }
   }

   private void assertValidators(@Nullable Validator... validators) {
      Object target = this.getTarget();

      for (Validator validator : validators) {
         if (validator != null && target != null && !validator.supports(target.getClass())) {
            throw new IllegalStateException("Invalid target for Validator [" + validator + "]: " + target);
         }
      }
   }

   public void setExcludedValidators(Predicate<Validator> predicate) {
      this.excludedValidators = predicate;
   }

   public void addValidators(Validator... validators) {
      this.assertValidators(validators);
      this.validators.addAll(Arrays.asList(validators));
   }

   public void replaceValidators(Validator... validators) {
      this.assertValidators(validators);
      this.validators.clear();
      this.validators.addAll(Arrays.asList(validators));
   }

   public @Nullable Validator getValidator() {
      return !this.validators.isEmpty() ? this.validators.get(0) : null;
   }

   public List<Validator> getValidators() {
      return Collections.unmodifiableList(this.validators);
   }

   public List<Validator> getValidatorsToApply() {
      return this.excludedValidators != null
         ? this.validators.stream().filter(validator -> !this.excludedValidators.test(validator)).toList()
         : Collections.unmodifiableList(this.validators);
   }

   public void setConversionService(@Nullable ConversionService conversionService) {
      Assert.state(this.conversionService == null, "DataBinder is already initialized with ConversionService");
      this.conversionService = conversionService;
      if (this.bindingResult != null && conversionService != null) {
         this.bindingResult.initConversion(conversionService);
      }
   }

   public @Nullable ConversionService getConversionService() {
      return this.conversionService;
   }

   public void addCustomFormatter(Formatter<?> formatter) {
      FormatterPropertyEditorAdapter adapter = new FormatterPropertyEditorAdapter(formatter);
      this.getPropertyEditorRegistry().registerCustomEditor(adapter.getFieldType(), adapter);
   }

   public void addCustomFormatter(Formatter<?> formatter, String... fields) {
      FormatterPropertyEditorAdapter adapter = new FormatterPropertyEditorAdapter(formatter);
      Class<?> fieldType = adapter.getFieldType();
      if (ObjectUtils.isEmpty(fields)) {
         this.getPropertyEditorRegistry().registerCustomEditor(fieldType, adapter);
      } else {
         for (String field : fields) {
            this.getPropertyEditorRegistry().registerCustomEditor(fieldType, field, adapter);
         }
      }
   }

   public void addCustomFormatter(Formatter<?> formatter, Class<?>... fieldTypes) {
      FormatterPropertyEditorAdapter adapter = new FormatterPropertyEditorAdapter(formatter);
      if (ObjectUtils.isEmpty(fieldTypes)) {
         this.getPropertyEditorRegistry().registerCustomEditor(adapter.getFieldType(), adapter);
      } else {
         for (Class<?> fieldType : fieldTypes) {
            this.getPropertyEditorRegistry().registerCustomEditor(fieldType, adapter);
         }
      }
   }

   public void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
      this.getPropertyEditorRegistry().registerCustomEditor(requiredType, propertyEditor);
   }

   public void registerCustomEditor(@Nullable Class<?> requiredType, @Nullable String field, PropertyEditor propertyEditor) {
      this.getPropertyEditorRegistry().registerCustomEditor(requiredType, field, propertyEditor);
   }

   public @Nullable PropertyEditor findCustomEditor(@Nullable Class<?> requiredType, @Nullable String propertyPath) {
      return this.getPropertyEditorRegistry().findCustomEditor(requiredType, propertyPath);
   }

   public <T> @Nullable T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType) throws TypeMismatchException {
      return (T)this.getTypeConverter().convertIfNecessary(value, requiredType);
   }

   public <T> @Nullable T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType, @Nullable MethodParameter methodParam) throws TypeMismatchException {
      return (T)this.getTypeConverter().convertIfNecessary(value, requiredType, methodParam);
   }

   public <T> @Nullable T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType, @Nullable Field field) throws TypeMismatchException {
      return (T)this.getTypeConverter().convertIfNecessary(value, requiredType, field);
   }

   public <T> @Nullable T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType, @Nullable TypeDescriptor typeDescriptor) throws TypeMismatchException {
      return (T)this.getTypeConverter().convertIfNecessary(value, requiredType, typeDescriptor);
   }

   public void construct(DataBinder.ValueResolver valueResolver) {
      Assert.state(this.target == null, "Target instance already available");
      Assert.state(this.targetType != null, "Target type not set");
      this.target = this.createObject(this.targetType, "", valueResolver, 0);
      if (!this.getBindingResult().hasErrors()) {
         this.bindingResult = null;
         if (this.typeConverter != null) {
            this.typeConverter.registerCustomEditors(this.getPropertyAccessor());
         }
      }
   }

   private Object createObject(ResolvableType objectType, String nestedPath, DataBinder.ValueResolver valueResolver, int depth) {
      Class<?> clazz = objectType.resolve();
      boolean isOptional = clazz == Optional.class;
      clazz = isOptional ? objectType.resolveGeneric(new int[]{0}) : clazz;
      if (clazz == null) {
         throw new IllegalStateException("Insufficient type information to create instance of " + objectType);
      }

      int maxNestedPathDepth = this.getMaxNestedPathDepth();
      if (depth > maxNestedPathDepth) {
         throw new InvalidPropertyException(
            clazz, nestedPath.substring(0, nestedPath.length() - 1), "Nesting depth of property path exceeds the maximum of " + maxNestedPathDepth
         );
      }

      Object result = null;
      Constructor<?> ctor = BeanUtils.getResolvableConstructor(clazz);
      if (ctor.getParameterCount() == 0) {
         result = BeanUtils.instantiateClass(ctor, new Object[0]);
      } else {
         String[] paramNames = BeanUtils.getParameterNames(ctor);
         Class<?>[] paramTypes = ctor.getParameterTypes();
         Object[] args = new Object[paramTypes.length];
         Set<String> failedParamNames = new HashSet<>(4);

         for (int i = 0; i < paramNames.length; i++) {
            MethodParameter param = MethodParameter.forFieldAwareConstructor(ctor, i, paramNames[i]);
            String lookupName = null;
            if (this.nameResolver != null) {
               lookupName = this.nameResolver.resolveName(param);
            }

            if (lookupName == null) {
               lookupName = paramNames[i];
            }

            String paramPath = nestedPath + lookupName;
            Class<?> paramType = paramTypes[i];
            ResolvableType resolvableType = ResolvableType.forMethodParameter(param);
            Object value = valueResolver.resolveValue(paramPath, paramType);
            if (value == null) {
               if (List.class.isAssignableFrom(paramType)) {
                  value = this.createList(paramPath, paramType, resolvableType, valueResolver, depth);
               } else if (Map.class.isAssignableFrom(paramType)) {
                  value = this.createMap(paramPath, paramType, resolvableType, valueResolver, depth);
               } else if (paramType.isArray()) {
                  value = this.createArray(paramPath, paramType, resolvableType, valueResolver, depth);
               }
            }

            if (value == null && this.shouldConstructArgument(param) && this.hasValuesFor(paramPath, valueResolver)) {
               args[i] = this.createObject(resolvableType, paramPath + ".", valueResolver, depth + 1);
            } else {
               try {
                  if (value == null && (param.isOptional() || this.getBindingResult().hasErrors())) {
                     args[i] = param.getParameterType() == Optional.class ? Optional.empty() : null;
                  } else {
                     args[i] = this.convertIfNecessary(value, paramType, param);
                  }
               } catch (TypeMismatchException ex) {
                  args[i] = null;
                  failedParamNames.add(paramPath);
                  this.handleTypeMismatchException(ex, paramPath, paramType, value);
               }
            }
         }

         if (this.getBindingResult().hasErrors()) {
            for (int i = 0; i < paramNames.length; i++) {
               String paramPath = nestedPath + paramNames[i];
               if (!failedParamNames.contains(paramPath)) {
                  Object value = args[i];
                  this.getBindingResult().recordFieldValue(paramPath, paramTypes[i], value);
                  this.validateConstructorArgument(ctor.getDeclaringClass(), nestedPath, paramNames[i], value);
               }
            }

            if (!(objectType.getSource() instanceof MethodParameter param && param.isOptional())) {
               try {
                  result = BeanUtils.instantiateClass(ctor, args);
               } catch (BeanInstantiationException var22) {
               }
            }
         } else {
            try {
               result = BeanUtils.instantiateClass(ctor, args);
            } catch (BeanInstantiationException ex) {
               if (!(KotlinDetector.isKotlinType(clazz) && ex.getCause() instanceof NullPointerException cause)) {
                  throw ex;
               }

               ObjectError error = new ObjectError(ctor.getName(), cause.getMessage());
               this.getBindingResult().addError(error);
            }
         }
      }

      return isOptional && !nestedPath.isEmpty() ? Optional.ofNullable(result) : result;
   }

   protected boolean shouldConstructArgument(MethodParameter param) {
      Class<?> type = param.nestedIfOptional().getNestedParameterType();
      return !BeanUtils.isSimpleValueType(type) && !type.getPackageName().startsWith("java.");
   }

   private boolean hasValuesFor(String paramPath, DataBinder.ValueResolver resolver) {
      for (String name : resolver.getNames()) {
         if (name.startsWith(paramPath + ".")) {
            return true;
         }
      }

      return false;
   }

   private @Nullable List<?> createList(String paramPath, Class<?> paramType, ResolvableType type, DataBinder.ValueResolver valueResolver, int depth) {
      ResolvableType elementType = type.getNested(2);
      SortedSet<Integer> indexes = getIndexes(paramPath, valueResolver);
      if (indexes == null) {
         return null;
      }

      int lastIndex = Math.max(indexes.last(), 0);
      int size = lastIndex < this.autoGrowCollectionLimit ? lastIndex + 1 : 0;
      List<?> list = (List<?>)CollectionFactory.createCollection(paramType, size);

      for (int i = 0; i < size; i++) {
         list.add(null);
      }

      for (int index : indexes) {
         String indexedPath = paramPath + "[" + (index != -1 ? index : "") + "]";
         ((List<Object>)list).set(Math.max(index, 0), this.createIndexedValue(paramPath, paramType, elementType, indexedPath, valueResolver, depth));
      }

      return list;
   }

   private <V> @Nullable Map<String, V> createMap(String paramPath, Class<?> paramType, ResolvableType type, DataBinder.ValueResolver valueResolver, int depth) {
      ResolvableType elementType = type.getNested(2);
      Map<String, V> map = null;

      for (String name : valueResolver.getNames()) {
         if (name.startsWith(paramPath + "[")) {
            int startIdx = paramPath.length() + 1;
            int endIdx = name.indexOf(93, startIdx);
            boolean quoted = endIdx - startIdx > 2 && name.charAt(startIdx) == '\'' && name.charAt(endIdx - 1) == '\'';
            String key = quoted ? name.substring(startIdx + 1, endIdx - 1) : name.substring(startIdx, endIdx);
            if (map == null) {
               map = CollectionFactory.createMap(paramType, 16);
            } else if (map.containsKey(key)) {
               continue;
            }

            String indexedPath = name.substring(0, endIdx + 1);
            map.put(key, this.createIndexedValue(paramPath, paramType, elementType, indexedPath, valueResolver, depth));
         }
      }

      return map;
   }

   private <V> V[] createArray(String paramPath, Class<?> paramType, ResolvableType type, DataBinder.ValueResolver valueResolver, int depth) {
      ResolvableType elementType = type.getNested(2);
      SortedSet<Integer> indexes = getIndexes(paramPath, valueResolver);
      if (indexes == null) {
         return null;
      }

      int lastIndex = Math.max(indexes.last(), 0);
      int size = lastIndex < this.autoGrowCollectionLimit ? lastIndex + 1 : 0;
      V[] array = (V[])Array.newInstance(elementType.resolve(), size);

      for (int index : indexes) {
         String indexedPath = paramPath + "[" + (index != -1 ? index : "") + "]";
         array[Math.max(index, 0)] = this.createIndexedValue(paramPath, paramType, elementType, indexedPath, valueResolver, depth);
      }

      return array;
   }

   private static @Nullable SortedSet<Integer> getIndexes(String paramPath, DataBinder.ValueResolver valueResolver) {
      SortedSet<Integer> indexes = null;

      for (String name : valueResolver.getNames()) {
         if (name.startsWith(paramPath + "[")) {
            int index;
            if (paramPath.length() + 2 == name.length()) {
               if (!name.endsWith("[]")) {
                  continue;
               }

               index = -1;
            } else {
               int endIndex = name.indexOf(93, paramPath.length() + 2);
               String indexValue = name.substring(paramPath.length() + 1, endIndex);
               index = Integer.parseInt(indexValue);
            }

            indexes = indexes != null ? indexes : new TreeSet<>();
            indexes.add(index);
         }
      }

      return indexes;
   }

   private <V> @Nullable V createIndexedValue(
      String paramPath, Class<?> containerType, ResolvableType elementType, String indexedPath, DataBinder.ValueResolver valueResolver, int depth
   ) {
      Object value = null;
      Class<?> elementClass = elementType.resolve(Object.class);
      if (List.class.isAssignableFrom(elementClass)) {
         value = this.createList(indexedPath, elementClass, elementType, valueResolver, depth);
      } else if (Map.class.isAssignableFrom(elementClass)) {
         value = this.createMap(indexedPath, elementClass, elementType, valueResolver, depth);
      } else if (elementClass.isArray()) {
         value = this.createArray(indexedPath, elementClass, elementType, valueResolver, depth);
      } else {
         Object rawValue = valueResolver.resolveValue(indexedPath, elementClass);
         if (rawValue != null) {
            try {
               value = this.convertIfNecessary(rawValue, elementClass);
            } catch (TypeMismatchException ex) {
               this.handleTypeMismatchException(ex, paramPath, containerType, rawValue);
            }
         } else {
            value = this.createObject(elementType, indexedPath + ".", valueResolver, depth + 1);
         }
      }

      return (V)value;
   }

   private void handleTypeMismatchException(TypeMismatchException ex, String paramPath, Class<?> paramType, @Nullable Object value) {
      ex.initPropertyName(paramPath);
      this.getBindingResult().recordFieldValue(paramPath, paramType, value);
      this.getBindingErrorProcessor().processPropertyAccessException(ex, this.getBindingResult());
   }

   private void validateConstructorArgument(Class<?> constructorClass, String nestedPath, @Nullable String name, @Nullable Object value) {
      Object[] hints = null;
      if (this.targetType != null && this.targetType.getSource() instanceof MethodParameter parameter) {
         for (Annotation ann : parameter.getParameterAnnotations()) {
            hints = ValidationAnnotationUtils.determineValidationHints(ann);
            if (hints != null) {
               break;
            }
         }
      }

      if (hints != null) {
         for (Validator validator : this.getValidatorsToApply()) {
            if (validator instanceof SmartValidator smartValidator) {
               boolean isNested = !nestedPath.isEmpty();
               if (isNested) {
                  this.getBindingResult().pushNestedPath(nestedPath.substring(0, nestedPath.length() - 1));
               }

               try {
                  smartValidator.validateValue(constructorClass, name, value, this.getBindingResult(), hints);
               } catch (IllegalArgumentException var11) {
               }

               if (isNested) {
                  this.getBindingResult().popNestedPath();
               }
            }
         }
      }
   }

   public void bind(PropertyValues pvs) {
      if (!this.shouldNotBindPropertyValues()) {
         MutablePropertyValues mpvs = pvs instanceof MutablePropertyValues mutablePropertyValues ? mutablePropertyValues : new MutablePropertyValues(pvs);
         this.doBind(mpvs);
      }
   }

   protected boolean shouldNotBindPropertyValues() {
      return this.isDeclarativeBinding() && ObjectUtils.isEmpty(this.allowedFields);
   }

   protected void doBind(MutablePropertyValues mpvs) {
      this.checkAllowedFields(mpvs);
      this.checkRequiredFields(mpvs);
      this.applyPropertyValues(mpvs);
   }

   private static String @Nullable [] canonicalPropertyNames(String @Nullable [] fields) {
      if (fields == null) {
         return null;
      }

      String[] result = new String[fields.length];

      for (int i = 0; i < fields.length; i++) {
         result[i] = PropertyPath.canonicalNameOrOriginal(fields[i]);
      }

      return result;
   }

   protected void checkAllowedFields(MutablePropertyValues mpvs) {
      PropertyValue[] pvs = mpvs.getPropertyValues();
      Options options = Options.withMaxNestedPathDepth(this.getMaxNestedPathDepth());

      for (PropertyValue pv : pvs) {
         PropertyPath field;
         try {
            field = PropertyPath.parse(pv.getName(), options);
         } catch (InvalidPropertyPathException ex) {
            mpvs.removePropertyValue(pv);
            Object target = this.getTarget();
            this.getBindingErrorProcessor()
               .processPropertyAccessException(
                  new InvalidPropertyPathException(target != null ? target : this, pv.getName(), pv.getValue(), ex), this.getInternalBindingResult()
               );
            continue;
         }

         if (!this.isAllowed(field.canonicalName())) {
            mpvs.removePropertyValue(pv);
            this.getBindingResult().recordSuppressedField(field.canonicalName());
            if (logger.isDebugEnabled()) {
               logger.debug(
                  "Field ["
                     + field
                     + "] has been removed from PropertyValues and will not be bound, because it has not been found in the list of allowed fields"
               );
            }
         }
      }
   }

   protected boolean isAllowed(String field) {
      String[] allowed = this.getAllowedFields();
      String[] disallowed = this.getDisallowedFields();
      if (!ObjectUtils.isEmpty(allowed) && !PatternMatchUtils.simpleMatch(allowed, field)) {
         return false;
      } else {
         return !ObjectUtils.isEmpty(disallowed) ? !PatternMatchUtils.simpleMatchIgnoreCase(disallowed, field) : true;
      }
   }

   protected void checkRequiredFields(MutablePropertyValues mpvs) {
      String[] requiredFields = this.getRequiredFields();
      if (!ObjectUtils.isEmpty(requiredFields)) {
         Map<String, PropertyValue> propertyValues = new HashMap<>();
         PropertyValue[] pvs = mpvs.getPropertyValues();

         for (PropertyValue pv : pvs) {
            String canonicalName = PropertyPath.canonicalNameOrOriginal(pv.getName());
            propertyValues.put(canonicalName, pv);
         }

         for (String field : requiredFields) {
            PropertyValue pv = propertyValues.get(field);
            boolean empty = pv == null || pv.getValue() == null;
            if (!empty) {
               if (pv.getValue() instanceof String text) {
                  empty = !StringUtils.hasText(text);
               } else if (pv.getValue() instanceof String[] values) {
                  empty = values.length == 0 || !StringUtils.hasText(values[0]);
               }
            }

            if (empty) {
               this.getBindingErrorProcessor().processMissingFieldError(field, this.getInternalBindingResult());
               if (pv != null) {
                  mpvs.removePropertyValue(pv);
                  propertyValues.remove(field);
               }
            }
         }
      }
   }

   protected void applyPropertyValues(MutablePropertyValues mpvs) {
      try {
         this.getPropertyAccessor().setPropertyValues(mpvs, this.isIgnoreUnknownFields(), this.isIgnoreInvalidFields());
      } catch (PropertyBatchUpdateException ex) {
         for (PropertyAccessException pae : ex.getPropertyAccessExceptions()) {
            this.getBindingErrorProcessor().processPropertyAccessException(pae, this.getInternalBindingResult());
         }
      }
   }

   public void validate() {
      Object target = this.getTarget();
      Assert.state(target != null, "No target to validate");
      BindingResult bindingResult = this.getBindingResult();

      for (Validator validator : this.getValidatorsToApply()) {
         validator.validate(target, bindingResult);
      }
   }

   public void validate(Object... validationHints) {
      Object target = this.getTarget();
      Assert.state(target != null, "No target to validate");
      BindingResult bindingResult = this.getBindingResult();

      for (Validator validator : this.getValidatorsToApply()) {
         if (!ObjectUtils.isEmpty(validationHints) && validator instanceof SmartValidator smartValidator) {
            smartValidator.validate(target, bindingResult, validationHints);
         } else if (validator != null) {
            validator.validate(target, bindingResult);
         }
      }
   }

   public Map<?, ?> close() throws BindException {
      if (this.getBindingResult().hasErrors()) {
         throw new BindException(this.getBindingResult());
      } else {
         return this.getBindingResult().getModel();
      }
   }

   private static class ExtendedTypeConverter extends SimpleTypeConverter implements PropertyEditorRegistrar {
      public void registerCustomEditors(PropertyEditorRegistry registry) {
         this.copyCustomEditorsTo(registry, null);
      }
   }

   public interface NameResolver {
      @Nullable String resolveName(MethodParameter parameter);
   }

   public interface ValueResolver {
      @Nullable Object resolveValue(String name, Class<?> type);

      Set<String> getNames();
   }
}
