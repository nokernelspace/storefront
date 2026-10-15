package org.springframework.web.service.invoker;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public abstract class AbstractNamedValueArgumentResolver implements HttpServiceArgumentResolver {
   private static final TypeDescriptor STRING_TARGET_TYPE = TypeDescriptor.valueOf(String.class);
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final @Nullable ConversionService conversionService;
   private final Map<MethodParameter, AbstractNamedValueArgumentResolver.NamedValueInfo> namedValueInfoCache = new ConcurrentHashMap<>(256);

   protected AbstractNamedValueArgumentResolver(ConversionService conversionService) {
      Assert.notNull(conversionService, "ConversionService is required");
      this.conversionService = conversionService;
   }

   protected AbstractNamedValueArgumentResolver() {
      this.conversionService = null;
   }

   @Override
   public boolean resolve(@Nullable Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
      AbstractNamedValueArgumentResolver.NamedValueInfo info = this.getNamedValueInfo(parameter, requestValues);
      if (info == null) {
         return false;
      }

      if (Map.class.isAssignableFrom(parameter.getParameterType())) {
         Assert.isInstanceOf(Map.class, argument);
         parameter = parameter.nested(1);
         argument = argument != null ? argument : Collections.emptyMap();

         for (Entry<String, ?> entry : ((Map)argument).entrySet()) {
            this.addSingleOrMultipleValues(entry.getKey(), entry.getValue(), false, null, info.label, info.multiValued, parameter, requestValues);
         }
      } else {
         this.addSingleOrMultipleValues(info.name, argument, info.required, info.defaultValue, info.label, info.multiValued, parameter, requestValues);
      }

      return true;
   }

   private AbstractNamedValueArgumentResolver.@Nullable NamedValueInfo getNamedValueInfo(MethodParameter parameter, HttpRequestValues.Metadata requestValues) {
      AbstractNamedValueArgumentResolver.NamedValueInfo info = this.namedValueInfoCache.get(parameter);
      if (info == null) {
         info = this.createNamedValueInfo(parameter, requestValues);
         if (info == null) {
            return null;
         }

         info = this.updateNamedValueInfo(parameter, info);
         this.namedValueInfoCache.put(parameter, info);
      }

      return info;
   }

   protected abstract AbstractNamedValueArgumentResolver.@Nullable NamedValueInfo createNamedValueInfo(MethodParameter parameter);

   protected AbstractNamedValueArgumentResolver.@Nullable NamedValueInfo createNamedValueInfo(MethodParameter parameter, HttpRequestValues.Metadata metadata) {
      return this.createNamedValueInfo(parameter);
   }

   private AbstractNamedValueArgumentResolver.NamedValueInfo updateNamedValueInfo(
      MethodParameter parameter, AbstractNamedValueArgumentResolver.NamedValueInfo info
   ) {
      String name = info.name;
      if (info.name.isEmpty()) {
         name = parameter.getParameterName();
         if (name == null) {
            throw new IllegalArgumentException(
               "Name for argument of type [%s] not specified, and parameter name information not available via reflection. Ensure that the compiler uses the '-parameters' flag."
                  .formatted(parameter.getNestedParameterType().getName())
            );
         }
      }

      boolean required = info.required && !parameter.isOptional();
      String defaultValue = "\n\t\t\n\t\t\n\ue000\ue001\ue002\n\t\t\t\t\n".equals(info.defaultValue) ? null : info.defaultValue;
      return info.update(name, required, defaultValue);
   }

   private void addSingleOrMultipleValues(
      String name,
      @Nullable Object value,
      boolean required,
      @Nullable Object defaultValue,
      String valueLabel,
      boolean supportsMultiValues,
      MethodParameter parameter,
      HttpRequestValues.Builder requestValues
   ) {
      if (supportsMultiValues) {
         if (ObjectUtils.isArray(value)) {
            value = Arrays.asList((Object[])value);
         }

         if (value instanceof Collection<?> elements) {
            parameter = parameter.nested();
            boolean hasValues = false;

            for (Object element : elements) {
               if (element != null) {
                  hasValues = true;
                  this.addSingleValue(name, element, false, null, valueLabel, parameter, requestValues);
               }
            }

            if (hasValues) {
               return;
            }

            value = null;
         }
      }

      this.addSingleValue(name, value, required, defaultValue, valueLabel, parameter, requestValues);
   }

   private void addSingleValue(
      String name,
      @Nullable Object value,
      boolean required,
      @Nullable Object defaultValue,
      String valueLabel,
      MethodParameter parameter,
      HttpRequestValues.Builder requestValues
   ) {
      if (value instanceof Optional<?> optionalValue) {
         value = optionalValue.orElse(null);
      }

      if (value == null && defaultValue != null) {
         value = defaultValue;
      }

      if (this.conversionService != null && !(value instanceof String)) {
         Object beforeValue = value;
         parameter = parameter.nestedIfOptional();
         Class<?> type = parameter.getNestedParameterType();
         value = type != Object.class && !type.isArray()
            ? this.conversionService.convert(value, new TypeDescriptor(parameter), STRING_TARGET_TYPE)
            : this.conversionService.convert(value, String.class);
         if (!StringUtils.hasText((String)value) && !required && beforeValue == null) {
            value = null;
         }
      }

      if (value == null) {
         Assert.isTrue(!required, () -> "Missing " + valueLabel + " value '" + name + "'");
      } else {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Resolved " + valueLabel + " value '" + name + ":" + value + "'");
         }

         this.addRequestValue(name, value, parameter, requestValues);
      }
   }

   protected abstract void addRequestValue(String name, Object value, MethodParameter parameter, HttpRequestValues.Builder requestValues);

   protected static class NamedValueInfo {
      private final String name;
      private final boolean required;
      private final @Nullable String defaultValue;
      private final String label;
      private final boolean multiValued;

      public NamedValueInfo(String name, boolean required, @Nullable String defaultValue, String label, boolean multiValued) {
         this.name = name;
         this.required = required;
         this.defaultValue = defaultValue;
         this.label = label;
         this.multiValued = multiValued;
      }

      public AbstractNamedValueArgumentResolver.NamedValueInfo update(String name, boolean required, @Nullable String defaultValue) {
         return new AbstractNamedValueArgumentResolver.NamedValueInfo(name, required, defaultValue, this.label, this.multiValued);
      }
   }
}
