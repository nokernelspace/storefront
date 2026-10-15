package org.springframework.beans;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;

public interface ConfigurablePropertyAccessor extends PropertyAccessor, PropertyEditorRegistry, TypeConverter {
   int DEFAULT_MAX_NESTED_PATH_DEPTH = 100;

   void setConversionService(@Nullable ConversionService conversionService);

   @Nullable ConversionService getConversionService();

   void setExtractOldValueForEditor(boolean extractOldValueForEditor);

   boolean isExtractOldValueForEditor();

   void setAutoGrowNestedPaths(boolean autoGrowNestedPaths);

   boolean isAutoGrowNestedPaths();

   void setAutoGrowCollectionLimit(int autoGrowCollectionLimit);

   int getAutoGrowCollectionLimit();

   void setMaxNestedPathDepth(int maxNestedPathDepth);

   int getMaxNestedPathDepth();
}
