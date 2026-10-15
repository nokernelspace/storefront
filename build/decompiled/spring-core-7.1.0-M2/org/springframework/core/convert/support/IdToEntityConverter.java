package org.springframework.core.convert.support;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

final class IdToEntityConverter implements ConditionalGenericConverter {
   private final ConversionService conversionService;

   public IdToEntityConverter(ConversionService conversionService) {
      this.conversionService = conversionService;
   }

   @Override
   public Set<GenericConverter.ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new GenericConverter.ConvertiblePair(Object.class, Object.class));
   }

   @Override
   public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      Method finder = this.getFinder(targetType.getType());
      return finder != null && this.conversionService.canConvert(sourceType, TypeDescriptor.valueOf(finder.getParameterTypes()[0]));
   }

   @Override
   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      if (source == null) {
         return null;
      }

      Method finder = this.getFinder(targetType.getType());
      Assert.state(finder != null, "No finder method");
      Object id = this.conversionService.convert(source, sourceType, TypeDescriptor.valueOf(finder.getParameterTypes()[0]));
      return ReflectionUtils.invokeMethod(finder, source, id);
   }

   private @Nullable Method getFinder(Class<?> entityClass) {
      String finderMethod = "find" + this.getEntityName(entityClass);

      Method[] methods;
      boolean localOnlyFiltered;
      try {
         methods = entityClass.getDeclaredMethods();
         localOnlyFiltered = true;
      } catch (SecurityException ex) {
         methods = entityClass.getMethods();
         localOnlyFiltered = false;
      }

      for (Method method : methods) {
         if (Modifier.isStatic(method.getModifiers())
            && method.getName().equals(finderMethod)
            && method.getParameterCount() == 1
            && method.getReturnType().equals(entityClass)
            && (localOnlyFiltered || method.getDeclaringClass().equals(entityClass))) {
            return method;
         }
      }

      return null;
   }

   private String getEntityName(Class<?> entityClass) {
      String shortName = ClassUtils.getShortName(entityClass);
      int lastDot = shortName.lastIndexOf(46);
      return lastDot != -1 ? shortName.substring(lastDot + 1) : shortName;
   }
}
