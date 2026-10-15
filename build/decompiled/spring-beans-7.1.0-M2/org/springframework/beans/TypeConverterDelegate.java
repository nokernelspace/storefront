package org.springframework.beans;

import java.beans.PropertyEditor;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.NumberUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

class TypeConverterDelegate {
   private static final Log logger = LogFactory.getLog(TypeConverterDelegate.class);
   private final PropertyEditorRegistrySupport propertyEditorRegistry;
   private final @Nullable Object targetObject;

   public TypeConverterDelegate(PropertyEditorRegistrySupport propertyEditorRegistry) {
      this(propertyEditorRegistry, null);
   }

   public TypeConverterDelegate(PropertyEditorRegistrySupport propertyEditorRegistry, @Nullable Object targetObject) {
      this.propertyEditorRegistry = propertyEditorRegistry;
      this.targetObject = targetObject;
   }

   public <T> @Nullable T convertIfNecessary(@Nullable String propertyName, @Nullable Object oldValue, Object newValue, @Nullable Class<T> requiredType) throws IllegalArgumentException {
      return this.convertIfNecessary(propertyName, oldValue, newValue, requiredType, TypeDescriptor.valueOf(requiredType));
   }

   public <T> @Nullable T convertIfNecessary(
      @Nullable String propertyName,
      @Nullable Object oldValue,
      @Nullable Object newValue,
      @Nullable Class<T> requiredType,
      @Nullable TypeDescriptor typeDescriptor
   ) throws IllegalArgumentException {
      PropertyEditor editor = this.propertyEditorRegistry.findCustomEditor(requiredType, propertyName);
      ConversionFailedException conversionAttemptEx = null;
      ConversionService conversionService = this.propertyEditorRegistry.getConversionService();
      if (editor == null && conversionService != null && newValue != null && typeDescriptor != null) {
         TypeDescriptor sourceTypeDesc = TypeDescriptor.forObject(newValue);
         if (conversionService.canConvert(sourceTypeDesc, typeDescriptor)) {
            try {
               return (T)conversionService.convert(newValue, sourceTypeDesc, typeDescriptor);
            } catch (ConversionFailedException ex) {
               conversionAttemptEx = ex;
            }
         }
      }

      Object convertedValue = newValue;
      if (editor != null || requiredType != null && !ClassUtils.isAssignableValue(requiredType, convertedValue)) {
         if (typeDescriptor != null && requiredType != null && Collection.class.isAssignableFrom(requiredType)) {
            TypeDescriptor elementTypeDesc = typeDescriptor.getElementTypeDescriptor();
            if (elementTypeDesc != null) {
               Class<?> elementType = elementTypeDesc.getType();
               if (convertedValue instanceof String text) {
                  if (Class.class == elementType || Enum.class.isAssignableFrom(elementType)) {
                     convertedValue = StringUtils.commaDelimitedListToStringArray(text);
                  }

                  if (editor == null && String.class != elementType) {
                     editor = this.findDefaultEditor(elementType.arrayType());
                  }
               }
            }
         }

         if (editor == null) {
            editor = this.findDefaultEditor(requiredType);
         }

         convertedValue = this.doConvertValue(oldValue, convertedValue, requiredType, editor);
      }

      boolean standardConversion = false;
      if (requiredType != null) {
         if (convertedValue != null) {
            if (Object.class == requiredType) {
               return (T)convertedValue;
            }

            if (requiredType.isArray()) {
               if (convertedValue instanceof String text && Enum.class.isAssignableFrom(requiredType.componentType())) {
                  convertedValue = StringUtils.commaDelimitedListToStringArray(text);
               }

               return (T)this.convertToTypedArray(convertedValue, propertyName, requiredType.componentType());
            }

            if (convertedValue.getClass().isArray()) {
               if (Collection.class.isAssignableFrom(requiredType)) {
                  convertedValue = this.convertToTypedCollection(CollectionUtils.arrayToList(convertedValue), propertyName, requiredType, typeDescriptor);
                  standardConversion = true;
               } else if (Array.getLength(convertedValue) == 1) {
                  convertedValue = Array.get(convertedValue, 0);
                  standardConversion = true;
               }
            } else if (convertedValue instanceof Collection<?> coll) {
               convertedValue = this.convertToTypedCollection(coll, propertyName, requiredType, typeDescriptor);
               standardConversion = true;
            } else if (convertedValue instanceof Map<?, ?> map) {
               convertedValue = this.convertToTypedMap(map, propertyName, requiredType, typeDescriptor);
               standardConversion = true;
            }

            if (String.class == requiredType && ClassUtils.isPrimitiveOrWrapper(convertedValue.getClass())) {
               return (T)convertedValue.toString();
            }

            if (convertedValue instanceof String text && !requiredType.isInstance(convertedValue)) {
               if (conversionAttemptEx == null && !requiredType.isInterface() && !requiredType.isEnum()) {
                  try {
                     Constructor<T> strCtor = requiredType.getConstructor(String.class);
                     return BeanUtils.instantiateClass(strCtor, convertedValue);
                  } catch (NoSuchMethodException ex) {
                     if (logger.isTraceEnabled()) {
                        logger.trace("No String constructor found on type [" + requiredType.getName() + "]", ex);
                     }
                  } catch (Exception ex) {
                     if (logger.isDebugEnabled()) {
                        logger.debug("Construction via String failed for type [" + requiredType.getName() + "]", ex);
                     }
                  }
               }

               String trimmedValue = text.trim();
               if (requiredType.isEnum() && trimmedValue.isEmpty()) {
                  return null;
               }

               convertedValue = this.attemptToConvertStringToEnum(requiredType, trimmedValue, convertedValue);
               standardConversion = true;
            } else if (convertedValue instanceof Number num && Number.class.isAssignableFrom(requiredType)) {
               convertedValue = NumberUtils.convertNumberToTargetClass(num, requiredType);
               standardConversion = true;
            }
         } else if (requiredType == Optional.class) {
            convertedValue = Optional.empty();
         }

         if (!ClassUtils.isAssignableValue(requiredType, convertedValue)) {
            if (conversionAttemptEx != null) {
               throw conversionAttemptEx;
            }

            if (conversionService != null && typeDescriptor != null) {
               TypeDescriptor sourceTypeDesc = TypeDescriptor.forObject(newValue);
               if (conversionService.canConvert(sourceTypeDesc, typeDescriptor)) {
                  return (T)conversionService.convert(newValue, sourceTypeDesc, typeDescriptor);
               }
            }

            StringBuilder msg = new StringBuilder();
            msg.append("Cannot convert value of type '").append(ClassUtils.getDescriptiveType(newValue));
            msg.append("' to required type '").append(ClassUtils.getQualifiedName(requiredType)).append('\'');
            if (propertyName != null) {
               msg.append(" for property '").append(propertyName).append('\'');
            }

            if (editor != null) {
               msg.append(": PropertyEditor [")
                  .append(editor.getClass().getName())
                  .append("] returned inappropriate value of type '")
                  .append(ClassUtils.getDescriptiveType(convertedValue))
                  .append('\'');
               throw new IllegalArgumentException(msg.toString());
            }

            msg.append(": no matching editors or conversion strategy found");
            throw new IllegalStateException(msg.toString());
         }
      }

      if (conversionAttemptEx != null) {
         if (editor == null && !standardConversion && requiredType != null && Object.class != requiredType) {
            throw conversionAttemptEx;
         }

         logger.debug("Original ConversionService attempt failed - ignored since PropertyEditor based conversion eventually succeeded", conversionAttemptEx);
      }

      return (T)convertedValue;
   }

   private Object attemptToConvertStringToEnum(Class<?> requiredType, String trimmedValue, Object currentConvertedValue) {
      Object convertedValue = currentConvertedValue;
      if (Enum.class == requiredType && this.targetObject != null) {
         int index = trimmedValue.lastIndexOf(46);
         if (index > -1) {
            String enumType = trimmedValue.substring(0, index);
            String fieldName = trimmedValue.substring(index + 1);
            ClassLoader cl = this.targetObject.getClass().getClassLoader();

            try {
               Class<?> enumValueType = ClassUtils.forName(enumType, cl);
               if (enumValueType.isEnum()) {
                  Field enumField = enumValueType.getField(fieldName);
                  convertedValue = enumField.get(null);
               } else if (logger.isTraceEnabled()) {
                  logger.trace("Specified enum class [" + enumType + "] is not a Java enum");
               }
            } catch (ClassNotFoundException ex) {
               if (logger.isTraceEnabled()) {
                  logger.trace("Enum class [" + enumType + "] cannot be loaded", ex);
               }
            } catch (Throwable ex) {
               if (logger.isTraceEnabled()) {
                  logger.trace("Field [" + fieldName + "] isn't an enum value for type [" + enumType + "]", ex);
               }
            }
         }
      }

      if (convertedValue == currentConvertedValue) {
         try {
            Field enumField = requiredType.getField(trimmedValue);
            ReflectionUtils.makeAccessible(enumField);
            convertedValue = enumField.get(null);
         } catch (Throwable ex) {
            if (logger.isTraceEnabled()) {
               logger.trace("Field [" + convertedValue + "] isn't an enum value", ex);
            }
         }
      }

      return convertedValue;
   }

   private @Nullable PropertyEditor findDefaultEditor(@Nullable Class<?> requiredType) {
      PropertyEditor editor = null;
      if (requiredType != null) {
         editor = this.propertyEditorRegistry.getDefaultEditor(requiredType);
         if (editor == null && String.class != requiredType) {
            editor = BeanUtils.findEditorByConvention(requiredType);
         }
      }

      return editor;
   }

   private @Nullable Object doConvertValue(
      @Nullable Object oldValue, @Nullable Object newValue, @Nullable Class<?> requiredType, @Nullable PropertyEditor editor
   ) {
      Object convertedValue = newValue;
      if (editor != null && !(convertedValue instanceof String)) {
         try {
            editor.setValue(convertedValue);
            Object newConvertedValue = editor.getValue();
            if (newConvertedValue != convertedValue) {
               convertedValue = newConvertedValue;
               editor = null;
            }
         } catch (Exception ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("PropertyEditor [" + editor.getClass().getName() + "] does not support setValue call", ex);
            }
         }
      }

      Object returnValue = convertedValue;
      if (requiredType != null && !requiredType.isArray() && convertedValue instanceof String[] array) {
         if (logger.isTraceEnabled()) {
            logger.trace("Converting String array to comma-delimited String [" + convertedValue + "]");
         }

         convertedValue = StringUtils.arrayToCommaDelimitedString(array);
      }

      if (convertedValue instanceof String newTextValue) {
         if (editor != null) {
            if (logger.isTraceEnabled()) {
               logger.trace("Converting String to [" + requiredType + "] using property editor [" + editor + "]");
            }

            return this.doConvertTextValue(oldValue, newTextValue, editor);
         }

         if (String.class == requiredType) {
            returnValue = convertedValue;
         }
      }

      return returnValue;
   }

   private Object doConvertTextValue(@Nullable Object oldValue, String newTextValue, PropertyEditor editor) {
      try {
         editor.setValue(oldValue);
      } catch (Exception ex) {
         if (logger.isDebugEnabled()) {
            logger.debug("PropertyEditor [" + editor.getClass().getName() + "] does not support setValue call", ex);
         }
      }

      editor.setAsText(newTextValue);
      return editor.getValue();
   }

   private Object convertToTypedArray(Object input, @Nullable String propertyName, Class<?> componentType) {
      if (input instanceof Collection<?> coll) {
         Object result = Array.newInstance(componentType, coll.size());
         int i = 0;

         for (Iterator<?> it = coll.iterator(); it.hasNext(); i++) {
            Object value = this.convertIfNecessary(this.buildIndexedPropertyName(propertyName, i), null, it.next(), componentType);
            Array.set(result, i, value);
         }

         return result;
      } else {
         if (!input.getClass().isArray()) {
            Object result = Array.newInstance(componentType, 1);
            Object value = this.convertIfNecessary(this.buildIndexedPropertyName(propertyName, 0), null, input, componentType);
            Array.set(result, 0, value);
            return result;
         }

         if (componentType.equals(input.getClass().componentType()) && !this.propertyEditorRegistry.hasCustomEditorForElement(componentType, propertyName)) {
            return input;
         }

         int arrayLength = Array.getLength(input);
         Object result = Array.newInstance(componentType, arrayLength);

         for (int i = 0; i < arrayLength; i++) {
            Object value = this.convertIfNecessary(this.buildIndexedPropertyName(propertyName, i), null, Array.get(input, i), componentType);
            Array.set(result, i, value);
         }

         return result;
      }
   }

   private Collection<?> convertToTypedCollection(
      Collection<?> original, @Nullable String propertyName, Class<?> requiredType, @Nullable TypeDescriptor typeDescriptor
   ) {
      if (!Collection.class.isAssignableFrom(requiredType)) {
         return original;
      }

      boolean approximable = CollectionFactory.isApproximableCollectionType(requiredType);
      if (!approximable && !this.canCreateCopy(requiredType)) {
         if (logger.isDebugEnabled()) {
            logger.debug(
               "Custom Collection type [" + original.getClass().getName() + "] does not allow for creating a copy - injecting original Collection as-is"
            );
         }

         return original;
      } else {
         boolean originalAllowed = requiredType.isInstance(original);
         TypeDescriptor elementType = typeDescriptor != null ? typeDescriptor.getElementTypeDescriptor() : null;
         if (elementType == null && originalAllowed && !this.propertyEditorRegistry.hasCustomEditorForElement(null, propertyName)) {
            return original;
         }

         Iterator<?> it;
         try {
            it = original.iterator();
         } catch (Throwable ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Cannot access Collection of type [" + original.getClass().getName() + "] - injecting original Collection as-is: " + ex);
            }

            return original;
         }

         Collection<Object> convertedCopy;
         try {
            if (approximable && requiredType.isInstance(original)) {
               convertedCopy = CollectionFactory.createApproximateCollection(original, original.size());
            } else {
               convertedCopy = CollectionFactory.createCollection(requiredType, original.size());
            }
         } catch (Throwable ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Cannot create copy of Collection type [" + original.getClass().getName() + "] - injecting original Collection as-is: " + ex);
            }

            return original;
         }

         for (int i = 0; it.hasNext(); i++) {
            Object element = it.next();
            String indexedPropertyName = this.buildIndexedPropertyName(propertyName, i);
            Object convertedElement = this.convertIfNecessary(
               indexedPropertyName, null, element, elementType != null ? elementType.getType() : null, elementType
            );

            try {
               convertedCopy.add(convertedElement);
            } catch (Throwable ex) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Collection type [" + original.getClass().getName() + "] seems to be read-only - injecting original Collection as-is: " + ex);
               }

               return original;
            }

            originalAllowed = originalAllowed && element == convertedElement;
         }

         return originalAllowed ? original : convertedCopy;
      }
   }

   private Map<?, ?> convertToTypedMap(Map<?, ?> original, @Nullable String propertyName, Class<?> requiredType, @Nullable TypeDescriptor typeDescriptor) {
      if (!Map.class.isAssignableFrom(requiredType)) {
         return original;
      }

      boolean approximable = CollectionFactory.isApproximableMapType(requiredType);
      if (!approximable && !this.canCreateCopy(requiredType)) {
         if (logger.isDebugEnabled()) {
            logger.debug("Custom Map type [" + original.getClass().getName() + "] does not allow for creating a copy - injecting original Map as-is");
         }

         return original;
      } else {
         boolean originalAllowed = requiredType.isInstance(original);
         TypeDescriptor keyType = typeDescriptor != null ? typeDescriptor.getMapKeyTypeDescriptor() : null;
         TypeDescriptor valueType = typeDescriptor != null ? typeDescriptor.getMapValueTypeDescriptor() : null;
         if (keyType == null && valueType == null && originalAllowed && !this.propertyEditorRegistry.hasCustomEditorForElement(null, propertyName)) {
            return original;
         }

         Iterator<?> it;
         try {
            it = original.entrySet().iterator();
         } catch (Throwable ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Cannot access Map of type [" + original.getClass().getName() + "] - injecting original Map as-is: " + ex);
            }

            return original;
         }

         Map<Object, Object> convertedCopy;
         try {
            if (approximable && requiredType.isInstance(original)) {
               convertedCopy = CollectionFactory.createApproximateMap(original, original.size());
            } else {
               convertedCopy = CollectionFactory.createMap(requiredType, original.size());
            }
         } catch (Throwable ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Cannot create copy of Map type [" + original.getClass().getName() + "] - injecting original Map as-is: " + ex);
            }

            return original;
         }

         while (it.hasNext()) {
            Entry<?, ?> entry = (Entry<?, ?>)it.next();
            Object key = entry.getKey();
            Object value = entry.getValue();
            String keyedPropertyName = this.buildKeyedPropertyName(propertyName, key);
            Object convertedKey = this.convertIfNecessary(keyedPropertyName, null, key, keyType != null ? keyType.getType() : null, keyType);
            Object convertedValue = this.convertIfNecessary(keyedPropertyName, null, value, valueType != null ? valueType.getType() : null, valueType);

            try {
               convertedCopy.put(convertedKey, convertedValue);
            } catch (Throwable ex) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Map type [" + original.getClass().getName() + "] seems to be read-only - injecting original Map as-is: " + ex);
               }

               return original;
            }

            originalAllowed = originalAllowed && key == convertedKey && value == convertedValue;
         }

         return originalAllowed ? original : convertedCopy;
      }
   }

   private @Nullable String buildIndexedPropertyName(@Nullable String propertyName, int index) {
      return propertyName != null ? propertyName + "[" + index + "]" : null;
   }

   private @Nullable String buildKeyedPropertyName(@Nullable String propertyName, Object key) {
      return propertyName != null ? propertyName + "[" + key + "]" : null;
   }

   private boolean canCreateCopy(Class<?> requiredType) {
      return !requiredType.isInterface()
         && !Modifier.isAbstract(requiredType.getModifiers())
         && Modifier.isPublic(requiredType.getModifiers())
         && ClassUtils.hasConstructor(requiredType, new Class[0]);
   }
}
