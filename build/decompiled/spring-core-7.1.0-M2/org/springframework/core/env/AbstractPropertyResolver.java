package org.springframework.core.env;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.SpringProperties;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.SystemPropertyUtils;

public abstract class AbstractPropertyResolver implements ConfigurablePropertyResolver {
   public static final String DEFAULT_PLACEHOLDER_ESCAPE_CHARACTER_PROPERTY_NAME = "spring.placeholder.escapeCharacter.default";
   static final Character UNDEFINED_ESCAPE_CHARACTER = '\u0000';
   static volatile @Nullable Character defaultEscapeCharacter = UNDEFINED_ESCAPE_CHARACTER;
   protected final Log logger = LogFactory.getLog(this.getClass());
   private volatile @Nullable ConfigurableConversionService conversionService;
   private @Nullable PropertyPlaceholderHelper nonStrictHelper;
   private @Nullable PropertyPlaceholderHelper strictHelper;
   private boolean ignoreUnresolvableNestedPlaceholders = false;
   private String placeholderPrefix = "${";
   private String placeholderSuffix = "}";
   private @Nullable String valueSeparator = ":";
   private @Nullable Character escapeCharacter = getDefaultEscapeCharacter();
   private final Set<String> requiredProperties = new LinkedHashSet<>();

   @Override
   public ConfigurableConversionService getConversionService() {
      ConfigurableConversionService cs = this.conversionService;
      if (cs == null) {
         synchronized (this) {
            cs = this.conversionService;
            if (cs == null) {
               cs = new DefaultConversionService();
               this.conversionService = cs;
            }
         }
      }

      return cs;
   }

   @Override
   public void setConversionService(ConfigurableConversionService conversionService) {
      Assert.notNull(conversionService, "ConversionService must not be null");
      this.conversionService = conversionService;
   }

   @Override
   public void setPlaceholderPrefix(String placeholderPrefix) {
      Assert.notNull(placeholderPrefix, "'placeholderPrefix' must not be null");
      this.placeholderPrefix = placeholderPrefix;
   }

   @Override
   public void setPlaceholderSuffix(String placeholderSuffix) {
      Assert.notNull(placeholderSuffix, "'placeholderSuffix' must not be null");
      this.placeholderSuffix = placeholderSuffix;
   }

   @Override
   public void setValueSeparator(@Nullable String valueSeparator) {
      this.valueSeparator = valueSeparator;
   }

   @Override
   public void setEscapeCharacter(@Nullable Character escapeCharacter) {
      this.escapeCharacter = escapeCharacter;
   }

   @Override
   public void setIgnoreUnresolvableNestedPlaceholders(boolean ignoreUnresolvableNestedPlaceholders) {
      this.ignoreUnresolvableNestedPlaceholders = ignoreUnresolvableNestedPlaceholders;
   }

   @Override
   public void setRequiredProperties(String... requiredProperties) {
      Collections.addAll(this.requiredProperties, requiredProperties);
   }

   @Override
   public void validateRequiredProperties() {
      MissingRequiredPropertiesException ex = new MissingRequiredPropertiesException();

      for (String key : this.requiredProperties) {
         if (this.getProperty(key) == null) {
            ex.addMissingRequiredProperty(key);
         }
      }

      if (!ex.getMissingRequiredProperties().isEmpty()) {
         throw ex;
      }
   }

   @Override
   public boolean containsProperty(String key) {
      return this.getProperty(key) != null;
   }

   @Override
   public @Nullable String getProperty(String key) {
      return this.getProperty(key, String.class);
   }

   @Override
   public String getProperty(String key, String defaultValue) {
      String value = this.getProperty(key);
      return value != null ? value : defaultValue;
   }

   @Override
   public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
      T value = this.getProperty(key, targetType);
      return value != null ? value : defaultValue;
   }

   @Override
   public String getRequiredProperty(String key) throws IllegalStateException {
      String value = this.getProperty(key);
      if (value == null) {
         throw new IllegalStateException("Required key '" + key + "' not found");
      } else {
         return value;
      }
   }

   @Override
   public <T> T getRequiredProperty(String key, Class<T> valueType) throws IllegalStateException {
      T value = this.getProperty(key, valueType);
      if (value == null) {
         throw new IllegalStateException("Required key '" + key + "' not found");
      } else {
         return value;
      }
   }

   @Override
   public String resolvePlaceholders(String text) {
      if (this.nonStrictHelper == null) {
         this.nonStrictHelper = this.createPlaceholderHelper(true);
      }

      return this.doResolvePlaceholders(text, this.nonStrictHelper);
   }

   @Override
   public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
      if (this.strictHelper == null) {
         this.strictHelper = this.createPlaceholderHelper(false);
      }

      return this.doResolvePlaceholders(text, this.strictHelper);
   }

   protected String resolveNestedPlaceholders(String value) {
      if (value.isEmpty()) {
         return value;
      } else {
         return this.ignoreUnresolvableNestedPlaceholders ? this.resolvePlaceholders(value) : this.resolveRequiredPlaceholders(value);
      }
   }

   private PropertyPlaceholderHelper createPlaceholderHelper(boolean ignoreUnresolvablePlaceholders) {
      return new PropertyPlaceholderHelper(
         this.placeholderPrefix, this.placeholderSuffix, this.valueSeparator, this.escapeCharacter, ignoreUnresolvablePlaceholders
      );
   }

   private String doResolvePlaceholders(String text, PropertyPlaceholderHelper helper) {
      return helper.replacePlaceholders(text, this::getPropertyAsRawString);
   }

   protected <T> @Nullable T convertValueIfNecessary(Object value, @Nullable Class<T> targetType) {
      if (targetType == null) {
         return (T)value;
      }

      ConversionService conversionServiceToUse = this.conversionService;
      if (conversionServiceToUse == null) {
         if (ClassUtils.isAssignableValue(targetType, value)) {
            return (T)value;
         }

         conversionServiceToUse = DefaultConversionService.getSharedInstance();
      }

      return conversionServiceToUse.convert(value, targetType);
   }

   protected abstract @Nullable String getPropertyAsRawString(String key);

   public static @Nullable Character getDefaultEscapeCharacter() throws IllegalArgumentException {
      Character escapeCharacter = defaultEscapeCharacter;
      if (UNDEFINED_ESCAPE_CHARACTER.equals(escapeCharacter)) {
         String value = SpringProperties.getProperty("spring.placeholder.escapeCharacter.default");
         if (value != null) {
            if (value.isEmpty()) {
               escapeCharacter = null;
            } else {
               if (value.length() != 1) {
                  throw new IllegalArgumentException(
                     "Value [%s] for property [%s] must be a single character or an empty string"
                        .formatted(value, "spring.placeholder.escapeCharacter.default")
                  );
               }

               try {
                  escapeCharacter = value.charAt(0);
               } catch (Exception ex) {
                  throw new IllegalArgumentException(
                     "Failed to process value [%s] for property [%s]: %s".formatted(value, "spring.placeholder.escapeCharacter.default", ex.getMessage()), ex
                  );
               }

               Assert.isTrue(
                  !escapeCharacter.equals('\u0000'),
                  () -> "Value for property [%s] must not be Character.MIN_VALUE".formatted("spring.placeholder.escapeCharacter.default")
               );
            }
         } else {
            escapeCharacter = SystemPropertyUtils.ESCAPE_CHARACTER;
         }

         defaultEscapeCharacter = escapeCharacter;
      }

      return escapeCharacter;
   }
}
