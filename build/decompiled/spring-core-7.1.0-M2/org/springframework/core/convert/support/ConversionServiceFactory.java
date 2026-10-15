package org.springframework.core.convert.support;

import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.converter.GenericConverter;

public final class ConversionServiceFactory {
   private ConversionServiceFactory() {
   }

   public static void registerConverters(@Nullable Set<?> converters, ConverterRegistry registry) {
      if (converters != null) {
         for (Object candidate : converters) {
            if (candidate instanceof GenericConverter genericConverter) {
               registry.addConverter(genericConverter);
            } else if (candidate instanceof Converter<?, ?> converter) {
               registry.addConverter(converter);
            } else {
               if (!(candidate instanceof ConverterFactory<?, ?> converterFactory)) {
                  throw new IllegalArgumentException(
                     "Each converter object must implement one of the Converter, ConverterFactory, or GenericConverter interfaces"
                  );
               }

               registry.addConverterFactory(converterFactory);
            }
         }
      }
   }
}
