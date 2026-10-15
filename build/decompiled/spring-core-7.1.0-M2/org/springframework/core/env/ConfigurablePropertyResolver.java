package org.springframework.core.env;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.support.ConfigurableConversionService;

public interface ConfigurablePropertyResolver extends PropertyResolver {
   ConfigurableConversionService getConversionService();

   void setConversionService(ConfigurableConversionService conversionService);

   void setPlaceholderPrefix(String placeholderPrefix);

   void setPlaceholderSuffix(String placeholderSuffix);

   void setValueSeparator(@Nullable String valueSeparator);

   void setEscapeCharacter(@Nullable Character escapeCharacter);

   void setIgnoreUnresolvableNestedPlaceholders(boolean ignoreUnresolvableNestedPlaceholders);

   void setRequiredProperties(String... requiredProperties);

   void validateRequiredProperties() throws MissingRequiredPropertiesException;
}
