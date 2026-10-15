package org.springframework.beans;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.PrivilegedActionException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.CollectionFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public abstract class AbstractNestablePropertyAccessor extends AbstractPropertyAccessor {
   private static final Log logger = LogFactory.getLog(AbstractNestablePropertyAccessor.class);
   @Nullable Object wrappedObject;
   private String nestedPath = "";
   @Nullable Object rootObject;
   private @Nullable Map<PropertyPath.Segment, AbstractNestablePropertyAccessor> nestedPropertyAccessors;

   protected AbstractNestablePropertyAccessor() {
      this(true);
   }

   protected AbstractNestablePropertyAccessor(boolean registerDefaultEditors) {
      if (registerDefaultEditors) {
         this.registerDefaultEditors();
      }

      this.typeConverterDelegate = new TypeConverterDelegate(this);
   }

   protected AbstractNestablePropertyAccessor(Object object) {
      this.registerDefaultEditors();
      this.setWrappedInstance(object);
   }

   protected AbstractNestablePropertyAccessor(Class<?> clazz) {
      this.registerDefaultEditors();
      this.setWrappedInstance(BeanUtils.instantiateClass(clazz));
   }

   protected AbstractNestablePropertyAccessor(Object object, String nestedPath, Object rootObject) {
      this.registerDefaultEditors();
      this.setWrappedInstance(object, nestedPath, rootObject);
   }

   protected AbstractNestablePropertyAccessor(Object object, String nestedPath, AbstractNestablePropertyAccessor parent) {
      this.setWrappedInstance(object, nestedPath, parent.getWrappedInstance());
      this.setExtractOldValueForEditor(parent.isExtractOldValueForEditor());
      this.setAutoGrowNestedPaths(parent.isAutoGrowNestedPaths());
      this.setAutoGrowCollectionLimit(parent.getAutoGrowCollectionLimit());
      this.setMaxNestedPathDepth(parent.getMaxNestedPathDepth());
      this.setConversionService(parent.getConversionService());
   }

   public void setWrappedInstance(Object object) {
      this.setWrappedInstance(object, "", null);
   }

   public void setWrappedInstance(Object object, @Nullable String nestedPath, @Nullable Object rootObject) {
      this.wrappedObject = ObjectUtils.unwrapOptional(object);
      Assert.notNull(this.wrappedObject, "Target object must not be null");
      this.nestedPath = nestedPath != null ? nestedPath : "";
      this.rootObject = !this.nestedPath.isEmpty() ? rootObject : this.wrappedObject;
      this.nestedPropertyAccessors = null;
      this.typeConverterDelegate = new TypeConverterDelegate(this, this.wrappedObject);
   }

   public final Object getWrappedInstance() {
      Assert.state(this.wrappedObject != null, "No wrapped object");
      return this.wrappedObject;
   }

   public final Class<?> getWrappedClass() {
      return this.getWrappedInstance().getClass();
   }

   public final String getNestedPath() {
      return this.nestedPath;
   }

   public final Object getRootInstance() {
      Assert.state(this.rootObject != null, "No root object");
      return this.rootObject;
   }

   public final Class<?> getRootClass() {
      return this.getRootInstance().getClass();
   }

   @Override
   public void setPropertyValue(String propertyName, @Nullable Object value) throws BeansException {
      AbstractNestablePropertyAccessor.ResolvedProperty resolved;
      try {
         resolved = this.resolvePropertyPath(propertyName);
      } catch (InvalidPropertyPathException ex) {
         throw new InvalidPropertyPathException(this.getRootInstance(), this.nestedPath + propertyName, value, ex);
      } catch (NotReadablePropertyException ex) {
         throw new NotWritablePropertyException(
            this.getRootClass(), this.nestedPath + propertyName, "Nested property in path '" + propertyName + "' does not exist", ex
         );
      }

      resolved.accessor().setPropertyValue(resolved.segment(), new PropertyValue(propertyName, value));
   }

   @Override
   public void setPropertyValue(PropertyValue pv) throws BeansException {
      PropertyPath.Segment segment = (PropertyPath.Segment)pv.resolvedTokens;
      if (segment == null) {
         String propertyName = pv.getName();

         AbstractNestablePropertyAccessor.ResolvedProperty resolved;
         try {
            resolved = this.resolvePropertyPath(propertyName);
         } catch (InvalidPropertyPathException ex) {
            throw new InvalidPropertyPathException(this.getRootInstance(), this.nestedPath + propertyName, pv.getValue(), ex);
         } catch (NotReadablePropertyException ex) {
            throw new NotWritablePropertyException(
               this.getRootClass(), this.nestedPath + propertyName, "Nested property in path '" + propertyName + "' does not exist", ex
            );
         }

         segment = resolved.segment();
         if (resolved.accessor() == this) {
            pv.getOriginalPropertyValue().resolvedTokens = segment;
         }

         resolved.accessor().setPropertyValue(segment, pv);
      } else {
         this.setPropertyValue(segment, pv);
      }
   }

   protected void setPropertyValue(PropertyPath.Segment segment, PropertyValue pv) throws BeansException {
      if (!segment.keys().isEmpty()) {
         this.processKeyedProperty(segment, pv);
      } else {
         this.processLocalProperty(segment, pv);
      }
   }

   private void processKeyedProperty(PropertyPath.Segment segment, PropertyValue pv) {
      Object propValue = this.getPropertyHoldingValue(segment);
      AbstractNestablePropertyAccessor.PropertyHandler ph = this.getLocalPropertyHandler(segment.name());
      if (ph == null) {
         throw new InvalidPropertyException(this.getRootClass(), this.nestedPath + segment.name(), "No property handler found");
      }

      List<String> keys = segment.keys();
      String lastKey = keys.get(keys.size() - 1);
      String canonicalName = segment.toCanonicalName();
      if (propValue.getClass().isArray()) {
         Class<?> componentType = propValue.getClass().componentType();
         int arrayIndex = Integer.parseInt(lastKey);
         Object oldValue = null;

         try {
            if (this.isExtractOldValueForEditor() && arrayIndex < Array.getLength(propValue)) {
               oldValue = Array.get(propValue, arrayIndex);
            }

            Object convertedValue = this.convertIfNecessary(canonicalName, oldValue, pv.getValue(), componentType, ph.nested(keys.size()));
            int length = Array.getLength(propValue);
            if (arrayIndex >= length && arrayIndex < this.getAutoGrowCollectionLimit()) {
               Object newArray = Array.newInstance(componentType, arrayIndex + 1);
               System.arraycopy(propValue, 0, newArray, 0, length);
               String propName = segment.withoutLastKey().toCanonicalName();
               this.setPropertyValue(propName, newArray);
               propValue = this.getPropertyValue(propName);
            }

            Array.set(propValue, arrayIndex, convertedValue);
         } catch (IndexOutOfBoundsException ex) {
            throw new InvalidPropertyException(
               this.getRootClass(), this.nestedPath + canonicalName, "Invalid array index in property path '" + canonicalName + "'", ex
            );
         }
      } else if (propValue instanceof List list) {
         TypeDescriptor requiredType = ph.getCollectionType(keys.size());
         int index = Integer.parseInt(lastKey);
         Object oldValue = null;
         if (this.isExtractOldValueForEditor() && index < list.size()) {
            oldValue = list.get(index);
         }

         Object convertedValue = this.convertIfNecessary(canonicalName, oldValue, pv.getValue(), requiredType.getResolvableType().resolve(), requiredType);
         int size = list.size();
         if (index >= size && index < this.getAutoGrowCollectionLimit()) {
            for (int i = size; i < index; i++) {
               try {
                  list.add(null);
               } catch (NullPointerException ex) {
                  throw new InvalidPropertyException(
                     this.getRootClass(),
                     this.nestedPath + canonicalName,
                     "Cannot set element with index "
                        + index
                        + " in List of size "
                        + size
                        + ", accessed using property path '"
                        + canonicalName
                        + "': List does not support filling up gaps with null elements"
                  );
               }
            }

            list.add(convertedValue);
         } else {
            try {
               list.set(index, convertedValue);
            } catch (IndexOutOfBoundsException ex) {
               throw new InvalidPropertyException(
                  this.getRootClass(), this.nestedPath + canonicalName, "Invalid list index in property path '" + canonicalName + "'", ex
               );
            }
         }
      } else {
         if (!(propValue instanceof Map map)) {
            throw new InvalidPropertyException(
               this.getRootClass(),
               this.nestedPath + canonicalName,
               "Property referenced in indexed property path '"
                  + canonicalName
                  + "' is neither an array nor a List nor a Map; returned value was ["
                  + propValue
                  + "]"
            );
         }

         TypeDescriptor mapKeyType = ph.getMapKeyType(keys.size());
         TypeDescriptor mapValueType = ph.getMapValueType(keys.size());
         Object convertedMapKey = this.convertIfNecessary(null, null, lastKey, mapKeyType.getResolvableType().resolve(), mapKeyType);
         Object oldValue = null;
         if (this.isExtractOldValueForEditor()) {
            oldValue = map.get(convertedMapKey);
         }

         Object convertedMapValue = this.convertIfNecessary(canonicalName, oldValue, pv.getValue(), mapValueType.getResolvableType().resolve(), mapValueType);
         map.put(convertedMapKey, convertedMapValue);
      }
   }

   private Object getPropertyHoldingValue(PropertyPath.Segment segment) {
      PropertyPath.Segment getterSegment = segment.withoutLastKey();

      Object propValue;
      try {
         propValue = this.getPropertyValue(getterSegment);
      } catch (NotReadablePropertyException ex) {
         throw new NotWritablePropertyException(
            this.getRootClass(),
            this.nestedPath + segment.toCanonicalName(),
            "Cannot access indexed value in property referenced in indexed property path '" + segment.toCanonicalName() + "'",
            ex
         );
      }

      if (propValue == null) {
         if (!this.isAutoGrowNestedPaths()) {
            throw new NullValueInNestedPathException(
               this.getRootClass(),
               this.nestedPath + segment.toCanonicalName(),
               "Cannot access indexed value in property referenced in indexed property path '" + segment.toCanonicalName() + "': returned null"
            );
         }

         propValue = this.setDefaultValue(getterSegment);
      }

      return propValue;
   }

   private void processLocalProperty(PropertyPath.Segment segment, PropertyValue pv) {
      String name = segment.name();
      AbstractNestablePropertyAccessor.PropertyHandler ph = this.getLocalPropertyHandler(name);
      if (ph != null && ph.isWritable()) {
         Object oldValue = null;

         try {
            Object originalValue = pv.getValue();
            Object valueToApply = originalValue;
            if (!Boolean.FALSE.equals(pv.conversionNecessary)) {
               if (pv.isConverted()) {
                  valueToApply = pv.getConvertedValue();
               } else {
                  if (this.isExtractOldValueForEditor() && ph.isReadable()) {
                     try {
                        oldValue = ph.getValue();
                     } catch (Exception var10) {
                        Exception ex = var10;
                        if (var10 instanceof PrivilegedActionException pae) {
                           ex = pae.getException();
                        }

                        if (logger.isDebugEnabled()) {
                           logger.debug("Could not read previous value of property '" + this.nestedPath + name + "'", ex);
                        }
                     }
                  }

                  valueToApply = this.convertForProperty(name, oldValue, originalValue, ph.toTypeDescriptor());
               }

               pv.getOriginalPropertyValue().conversionNecessary = valueToApply != originalValue;
            }

            ph.setValue(valueToApply);
         } catch (TypeMismatchException ex) {
            if (!ph.setValueFallbackIfPossible(pv.getValue())) {
               throw ex;
            }
         } catch (InvocationTargetException ex) {
            PropertyChangeEvent propertyChangeEvent = new PropertyChangeEvent(this.getRootInstance(), this.nestedPath + name, oldValue, pv.getValue());
            if (ex.getTargetException() instanceof ClassCastException) {
               throw new TypeMismatchException(propertyChangeEvent, ph.getPropertyType(), ex.getTargetException());
            }

            Throwable cause = ex.getTargetException();
            if (cause instanceof UndeclaredThrowableException) {
               cause = cause.getCause();
            }

            throw new MethodInvocationException(propertyChangeEvent, cause);
         } catch (Exception ex) {
            PropertyChangeEvent pce = new PropertyChangeEvent(this.getRootInstance(), this.nestedPath + name, oldValue, pv.getValue());
            throw new MethodInvocationException(pce, ex);
         }
      } else if (pv.isOptional()) {
         if (logger.isDebugEnabled()) {
            logger.debug("Ignoring optional value for property '" + name + "' - property not found on bean class [" + this.getRootClass().getName() + "]");
         }
      } else if (!this.suppressNotWritablePropertyException) {
         throw this.createNotWritablePropertyException(name);
      }
   }

   @Override
   public @Nullable Class<?> getPropertyType(String propertyName) throws BeansException {
      if (this.wrappedObject == null) {
         return null;
      }

      try {
         AbstractNestablePropertyAccessor.PropertyHandler ph = this.getPropertyHandler(propertyName);
         if (ph != null) {
            return ph.getPropertyType();
         }

         Object value = this.getPropertyValue(propertyName);
         if (value != null) {
            return value.getClass();
         }

         Class<?> editorType = this.guessPropertyTypeFromEditors(propertyName);
         if (editorType != null) {
            return editorType;
         }
      } catch (InvalidPropertyException | InvalidPropertyPathException var5) {
      }

      return null;
   }

   @Override
   public @Nullable TypeDescriptor getPropertyTypeDescriptor(String propertyName) throws BeansException {
      try {
         AbstractNestablePropertyAccessor.ResolvedProperty resolved = this.resolvePropertyPath(propertyName);
         PropertyPath.Segment segment = resolved.segment();
         AbstractNestablePropertyAccessor.PropertyHandler ph = resolved.accessor().getLocalPropertyHandler(segment.name());
         if (ph != null) {
            List<String> keys = segment.keys();
            if (!keys.isEmpty()) {
               if (ph.isReadable() || ph.isWritable()) {
                  return ph.nested(keys.size());
               }
            } else if (ph.isReadable() || ph.isWritable()) {
               return ph.toTypeDescriptor();
            }
         }
      } catch (InvalidPropertyException | InvalidPropertyPathException var6) {
      }

      return null;
   }

   @Override
   public boolean isReadableProperty(String propertyName) {
      try {
         AbstractNestablePropertyAccessor.PropertyHandler ph = this.getPropertyHandler(propertyName);
         if (ph != null) {
            return ph.isReadable();
         }

         this.getPropertyValue(propertyName);
         return true;
      } catch (InvalidPropertyException | InvalidPropertyPathException var3) {
         return false;
      }
   }

   @Override
   public boolean isWritableProperty(String propertyName) {
      try {
         AbstractNestablePropertyAccessor.PropertyHandler ph = this.getPropertyHandler(propertyName);
         if (ph != null) {
            return ph.isWritable();
         }

         this.getPropertyValue(propertyName);
         return true;
      } catch (InvalidPropertyException | InvalidPropertyPathException var3) {
         return false;
      }
   }

   private @Nullable Object convertIfNecessary(
      @Nullable String propertyName, @Nullable Object oldValue, @Nullable Object newValue, @Nullable Class<?> requiredType, @Nullable TypeDescriptor td
   ) throws TypeMismatchException {
      Assert.state(this.typeConverterDelegate != null, "No TypeConverterDelegate");

      try {
         return this.typeConverterDelegate.convertIfNecessary(propertyName, oldValue, newValue, requiredType, td);
      } catch (ConverterNotFoundException | IllegalStateException ex) {
         PropertyChangeEvent pce = new PropertyChangeEvent(this.getRootInstance(), this.nestedPath + propertyName, oldValue, newValue);
         throw new ConversionNotSupportedException(pce, requiredType, ex);
      } catch (ConversionException | IllegalArgumentException ex) {
         PropertyChangeEvent pce = new PropertyChangeEvent(this.getRootInstance(), this.nestedPath + propertyName, oldValue, newValue);
         throw new TypeMismatchException(pce, requiredType, ex);
      }
   }

   protected @Nullable Object convertForProperty(String propertyName, @Nullable Object oldValue, @Nullable Object newValue, TypeDescriptor td) throws TypeMismatchException {
      return this.convertIfNecessary(propertyName, oldValue, newValue, td.getType(), td);
   }

   @Override
   public @Nullable Object getPropertyValue(String propertyName) throws BeansException {
      AbstractNestablePropertyAccessor.ResolvedProperty resolved = this.resolvePropertyPath(propertyName);
      return resolved.accessor().getPropertyValue(resolved.segment());
   }

   protected @Nullable Object getPropertyValue(PropertyPath.Segment segment) throws BeansException {
      String propertyName = segment.toCanonicalName();
      String actualName = segment.name();
      AbstractNestablePropertyAccessor.PropertyHandler ph = this.getLocalPropertyHandler(actualName);
      if (ph != null && ph.isReadable()) {
         try {
            Object value = ph.getValue();
            List<String> keys = segment.keys();
            if (!keys.isEmpty()) {
               if (value == null) {
                  if (!this.isAutoGrowNestedPaths()) {
                     throw new NullValueInNestedPathException(
                        this.getRootClass(),
                        this.nestedPath + propertyName,
                        "Cannot access indexed value of property referenced in indexed property path '" + propertyName + "': returned null"
                     );
                  }

                  value = this.setDefaultValue(new PropertyPath.Segment(actualName, List.of()));
               }

               StringBuilder indexedPropertyName = new StringBuilder(actualName);

               for (int i = 0; i < keys.size(); i++) {
                  String key = keys.get(i);
                  if (value == null) {
                     throw new NullValueInNestedPathException(
                        this.getRootClass(),
                        this.nestedPath + propertyName,
                        "Cannot access indexed value of property referenced in indexed property path '" + propertyName + "': returned null"
                     );
                  }

                  if (value.getClass().isArray()) {
                     int index = Integer.parseInt(key);
                     value = this.growArrayIfNecessary(value, index, indexedPropertyName.toString());
                     value = Array.get(value, index);
                  } else if (value instanceof List list) {
                     int index = Integer.parseInt(key);
                     this.growCollectionIfNecessary(list, index, indexedPropertyName.toString(), ph, i + 1);
                     if (index < 0 || index >= list.size()) {
                        throw new InvalidPropertyException(
                           this.getRootClass(),
                           this.nestedPath + propertyName,
                           "Cannot get element with index "
                              + index
                              + " from List of size "
                              + list.size()
                              + ", accessed using property path '"
                              + propertyName
                              + "'"
                        );
                     }

                     value = list.get(index);
                  } else if (value instanceof Map map) {
                     Class<?> mapKeyType = ph.getResolvableType().getNested(i + 1).asMap().resolveGeneric(new int[]{0});
                     TypeDescriptor typeDescriptor = TypeDescriptor.valueOf(mapKeyType);
                     Object convertedMapKey = this.convertIfNecessary(null, null, key, mapKeyType, typeDescriptor);
                     value = map.get(convertedMapKey);
                  } else {
                     if (!(value instanceof Iterable iterable)) {
                        throw new InvalidPropertyException(
                           this.getRootClass(),
                           this.nestedPath + propertyName,
                           "Property referenced in indexed property path '"
                              + propertyName
                              + "' is neither an array nor a List/Set/Collection/Iterable nor a Map; returned value was ["
                              + value
                              + "]"
                        );
                     }

                     int index = Integer.parseInt(key);
                     if (value instanceof Collection<?> coll && (index < 0 || index >= coll.size())) {
                        throw new InvalidPropertyException(
                           this.getRootClass(),
                           this.nestedPath + propertyName,
                           "Cannot get element with index "
                              + index
                              + " from Collection of size "
                              + coll.size()
                              + ", accessed using property path '"
                              + propertyName
                              + "'"
                        );
                     }

                     Iterator<Object> it = iterable.iterator();
                     boolean found = false;

                     int currIndex;
                     for (currIndex = 0; it.hasNext(); currIndex++) {
                        Object elem = it.next();
                        if (currIndex == index) {
                           value = elem;
                           found = true;
                           break;
                        }
                     }

                     if (!found) {
                        throw new InvalidPropertyException(
                           this.getRootClass(),
                           this.nestedPath + propertyName,
                           "Cannot get element with index "
                              + index
                              + " from Iterable of size "
                              + currIndex
                              + ", accessed using property path '"
                              + propertyName
                              + "'"
                        );
                     }
                  }

                  indexedPropertyName.append("[").append(key).append("]");
               }
            }

            return value;
         } catch (InvalidPropertyException ex) {
            throw ex;
         } catch (IndexOutOfBoundsException ex) {
            throw new InvalidPropertyException(
               this.getRootClass(), this.nestedPath + propertyName, "Index of out of bounds in property path '" + propertyName + "'", ex
            );
         } catch (NumberFormatException | TypeMismatchException ex) {
            throw new InvalidPropertyException(this.getRootClass(), this.nestedPath + propertyName, "Invalid index in property path '" + propertyName + "'", ex);
         } catch (InvocationTargetException ex) {
            throw new InvalidPropertyException(
               this.getRootClass(), this.nestedPath + propertyName, "Getter for property '" + actualName + "' threw exception", ex
            );
         } catch (Exception ex) {
            throw new InvalidPropertyException(
               this.getRootClass(), this.nestedPath + propertyName, "Illegal attempt to get property '" + actualName + "' threw exception", ex
            );
         }
      } else {
         throw new NotReadablePropertyException(this.getRootClass(), this.nestedPath + propertyName);
      }
   }

   protected AbstractNestablePropertyAccessor.@Nullable PropertyHandler getPropertyHandler(String propertyName) throws BeansException {
      Assert.notNull(propertyName, "Property name must not be null");
      AbstractNestablePropertyAccessor.ResolvedProperty resolved = this.resolvePropertyPath(propertyName);
      return resolved.accessor().getLocalPropertyHandler(resolved.segment().toCanonicalName());
   }

   protected abstract AbstractNestablePropertyAccessor.@Nullable PropertyHandler getLocalPropertyHandler(String propertyName);

   protected abstract AbstractNestablePropertyAccessor newNestedPropertyAccessor(Object object, String nestedPath);

   protected abstract NotWritablePropertyException createNotWritablePropertyException(String propertyName);

   private Object growArrayIfNecessary(Object array, int index, String name) {
      if (!this.isAutoGrowNestedPaths()) {
         return array;
      }

      int length = Array.getLength(array);
      if (index >= length && index < this.getAutoGrowCollectionLimit()) {
         Class<?> componentType = array.getClass().componentType();
         Object newArray = Array.newInstance(componentType, index + 1);
         System.arraycopy(array, 0, newArray, 0, length);

         for (int i = length; i < Array.getLength(newArray); i++) {
            Array.set(newArray, i, this.newValue(componentType, null, name));
         }

         this.setPropertyValue(name, newArray);
         Object defaultValue = this.getPropertyValue(name);
         Assert.state(defaultValue != null, "Default value must not be null");
         return defaultValue;
      } else {
         return array;
      }
   }

   private void growCollectionIfNecessary(
      Collection<Object> collection, int index, String name, AbstractNestablePropertyAccessor.PropertyHandler ph, int nestingLevel
   ) {
      if (this.isAutoGrowNestedPaths()) {
         int size = collection.size();
         if (index >= size && index < this.getAutoGrowCollectionLimit()) {
            Class<?> elementType = ph.getResolvableType().getNested(nestingLevel).asCollection().resolveGeneric(new int[0]);
            if (elementType != null) {
               for (int i = collection.size(); i < index + 1; i++) {
                  collection.add(this.newValue(elementType, null, name));
               }
            }
         }
      }
   }

   protected AbstractNestablePropertyAccessor.ResolvedProperty resolvePropertyPath(String propertyPath) {
      PropertyPath.Options options = PropertyPath.Options.withMaxNestedPathDepth(this.getMaxNestedPathDepth());
      List<PropertyPath.Segment> segments = PropertyPath.parse(propertyPath, options).segments();
      AbstractNestablePropertyAccessor accessor = this.getPropertyAccessorForSegments(segments, 0);
      PropertyPath.Segment segment = finalSegment(propertyPath, segments);
      return new AbstractNestablePropertyAccessor.ResolvedProperty(accessor, segment);
   }

   private static PropertyPath.Segment finalSegment(String propertyPath, List<PropertyPath.Segment> segments) {
      return segments.isEmpty() ? new PropertyPath.Segment(propertyPath, List.of()) : segments.get(segments.size() - 1);
   }

   private AbstractNestablePropertyAccessor getPropertyAccessorForSegments(List<PropertyPath.Segment> segments, int fromIndex) {
      if (!segments.isEmpty() && fromIndex < segments.size() - 1) {
         AbstractNestablePropertyAccessor nestedPa = this.getNestedPropertyAccessor(segments.get(fromIndex));
         return nestedPa.getPropertyAccessorForSegments(segments, fromIndex + 1);
      } else {
         return this;
      }
   }

   private AbstractNestablePropertyAccessor getNestedPropertyAccessor(PropertyPath.Segment segment) {
      Map<PropertyPath.Segment, AbstractNestablePropertyAccessor> nestedAccessors = this.nestedPropertyAccessors;
      if (nestedAccessors == null) {
         nestedAccessors = new HashMap<>();
         this.nestedPropertyAccessors = nestedAccessors;
      }

      Object value = this.getPropertyValue(segment);
      if (value == null || value instanceof Optional<?> optional && optional.isEmpty()) {
         if (!this.isAutoGrowNestedPaths()) {
            throw new NullValueInNestedPathException(this.getRootClass(), this.nestedPath + segment.toCanonicalName());
         }

         value = this.setDefaultValue(segment);
      }

      AbstractNestablePropertyAccessor nestedPa = nestedAccessors.get(segment);
      if (nestedPa == null || nestedPa.getWrappedInstance() != ObjectUtils.unwrapOptional(value)) {
         String canonicalName = segment.toCanonicalName();
         if (logger.isTraceEnabled()) {
            logger.trace("Creating new nested " + this.getClass().getSimpleName() + " for property '" + canonicalName + "'");
         }

         nestedPa = this.newNestedPropertyAccessor(value, this.nestedPath + canonicalName + ".");
         this.copyDefaultEditorsTo(nestedPa);
         this.copyCustomEditorsTo(nestedPa, canonicalName);
         nestedAccessors.put(segment, nestedPa);
      } else if (logger.isTraceEnabled()) {
         logger.trace("Using cached nested property accessor for property '" + segment.toCanonicalName() + "'");
      }

      return nestedPa;
   }

   private Object setDefaultValue(PropertyPath.Segment segment) {
      PropertyValue pv = this.createDefaultPropertyValue(segment);
      this.setPropertyValue(segment, pv);
      Object defaultValue = this.getPropertyValue(segment);
      Assert.state(defaultValue != null, "Default value must not be null");
      return defaultValue;
   }

   private PropertyValue createDefaultPropertyValue(PropertyPath.Segment segment) {
      String canonicalName = segment.toCanonicalName();
      TypeDescriptor desc = this.getPropertyTypeDescriptor(canonicalName);
      if (desc == null) {
         throw new NullValueInNestedPathException(
            this.getRootClass(), this.nestedPath + canonicalName, "Could not determine property type for auto-growing a default value"
         );
      }

      Object defaultValue = this.newValue(desc.getType(), desc, canonicalName);
      return new PropertyValue(canonicalName, defaultValue);
   }

   private Object newValue(Class<?> type, @Nullable TypeDescriptor desc, String name) {
      try {
         if (type.isArray()) {
            return createArray(type);
         } else if (Collection.class.isAssignableFrom(type)) {
            TypeDescriptor elementDesc = desc != null ? desc.getElementTypeDescriptor() : null;
            return CollectionFactory.createCollection(type, elementDesc != null ? elementDesc.getType() : null, 16);
         } else if (Map.class.isAssignableFrom(type)) {
            TypeDescriptor keyDesc = desc != null ? desc.getMapKeyTypeDescriptor() : null;
            return CollectionFactory.createMap(type, keyDesc != null ? keyDesc.getType() : null, 16);
         } else {
            Constructor<?> ctor = type.getDeclaredConstructor();
            if (Modifier.isPrivate(ctor.getModifiers())) {
               throw new IllegalAccessException("Auto-growing not allowed with private constructor: " + ctor);
            } else {
               return BeanUtils.instantiateClass(ctor);
            }
         }
      } catch (Throwable ex) {
         throw new NullValueInNestedPathException(
            this.getRootClass(), this.nestedPath + name, "Could not instantiate property type [" + type.getName() + "] to auto-grow nested property path", ex
         );
      }
   }

   private static Object createArray(Class<?> arrayType) {
      Assert.notNull(arrayType, "Array type must not be null");
      Class<?> componentType = arrayType.componentType();
      if (componentType.isArray()) {
         Object array = Array.newInstance(componentType, 1);
         Array.set(array, 0, createArray(componentType));
         return array;
      } else {
         return Array.newInstance(componentType, 0);
      }
   }

   @Override
   public String toString() {
      String className = this.getClass().getName();
      return this.wrappedObject == null
         ? className + ": no wrapped object set"
         : className + ": wrapping object [" + ObjectUtils.identityToString(this.wrappedObject) + "]";
   }

   protected abstract static class PropertyHandler {
      private final @Nullable Class<?> propertyType;
      private final boolean readable;
      private final boolean writable;

      public PropertyHandler(@Nullable Class<?> propertyType, boolean readable, boolean writable) {
         this.propertyType = propertyType;
         this.readable = readable;
         this.writable = writable;
      }

      public @Nullable Class<?> getPropertyType() {
         return this.propertyType;
      }

      public boolean isReadable() {
         return this.readable;
      }

      public boolean isWritable() {
         return this.writable;
      }

      public abstract TypeDescriptor toTypeDescriptor();

      public abstract ResolvableType getResolvableType();

      public TypeDescriptor getMapKeyType(int nestingLevel) {
         return TypeDescriptor.valueOf(this.getResolvableType().getNested(nestingLevel).asMap().resolveGeneric(new int[]{0}));
      }

      public TypeDescriptor getMapValueType(int nestingLevel) {
         return TypeDescriptor.valueOf(this.getResolvableType().getNested(nestingLevel).asMap().resolveGeneric(new int[]{1}));
      }

      public TypeDescriptor getCollectionType(int nestingLevel) {
         return TypeDescriptor.valueOf(this.getResolvableType().getNested(nestingLevel).asCollection().resolveGeneric(new int[0]));
      }

      public abstract @Nullable TypeDescriptor nested(int level);

      public abstract @Nullable Object getValue() throws Exception;

      public abstract void setValue(@Nullable Object value) throws Exception;

      public boolean setValueFallbackIfPossible(@Nullable Object value) {
         return false;
      }
   }

   protected record ResolvedProperty(AbstractNestablePropertyAccessor accessor, PropertyPath.Segment segment) {
   }
}
