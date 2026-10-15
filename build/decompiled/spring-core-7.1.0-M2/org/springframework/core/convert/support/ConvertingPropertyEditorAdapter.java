package org.springframework.core.convert.support;

import java.beans.PropertyEditorSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.Assert;

public class ConvertingPropertyEditorAdapter extends PropertyEditorSupport {
   private final ConversionService conversionService;
   private final TypeDescriptor targetDescriptor;
   private final boolean canConvertToString;

   public ConvertingPropertyEditorAdapter(ConversionService conversionService, TypeDescriptor targetDescriptor) {
      Assert.notNull(conversionService, "ConversionService must not be null");
      Assert.notNull(targetDescriptor, "TypeDescriptor must not be null");
      this.conversionService = conversionService;
      this.targetDescriptor = targetDescriptor;
      this.canConvertToString = conversionService.canConvert(this.targetDescriptor, TypeDescriptor.valueOf(String.class));
   }

   @Override
   public void setAsText(@Nullable String text) throws IllegalArgumentException {
      this.setValue(this.conversionService.convert(text, TypeDescriptor.valueOf(String.class), this.targetDescriptor));
   }

   @Override
   public @Nullable String getAsText() {
      return this.canConvertToString
         ? (String)this.conversionService.convert(this.getValue(), this.targetDescriptor, TypeDescriptor.valueOf(String.class))
         : null;
   }
}
