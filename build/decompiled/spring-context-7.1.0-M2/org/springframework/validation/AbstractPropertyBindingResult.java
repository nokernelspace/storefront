package org.springframework.validation;

import java.beans.PropertyEditor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyPath;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.ConvertingPropertyEditorAdapter;
import org.springframework.util.Assert;

public abstract class AbstractPropertyBindingResult extends AbstractBindingResult {
   private transient @Nullable ConversionService conversionService;

   protected AbstractPropertyBindingResult(String objectName) {
      super(objectName);
   }

   public void initConversion(ConversionService conversionService) {
      Assert.notNull(conversionService, "ConversionService must not be null");
      this.conversionService = conversionService;
      if (this.getTarget() != null) {
         this.getPropertyAccessor().setConversionService(conversionService);
      }
   }

   @Override
   public @Nullable PropertyEditorRegistry getPropertyEditorRegistry() {
      return this.getTarget() != null ? this.getPropertyAccessor() : null;
   }

   @Override
   protected String canonicalFieldName(String field) {
      return PropertyPath.canonicalNameOrOriginal(field);
   }

   @Override
   public @Nullable Class<?> getFieldType(@Nullable String field) {
      return this.getTarget() != null ? this.getPropertyAccessor().getPropertyType(this.fixedField(field)) : super.getFieldType(field);
   }

   @Override
   protected @Nullable Object getActualFieldValue(String field) {
      return this.getPropertyAccessor().getPropertyValue(field);
   }

   @Override
   protected @Nullable Object formatFieldValue(String field, @Nullable Object value) {
      String fixedField = this.fixedField(field);
      PropertyEditor customEditor = this.getCustomEditor(fixedField);
      if (customEditor != null) {
         customEditor.setValue(value);
         String textValue = customEditor.getAsText();
         if (textValue != null) {
            return textValue;
         }
      }

      if (this.conversionService != null) {
         TypeDescriptor fieldDesc = this.getPropertyAccessor().getPropertyTypeDescriptor(fixedField);
         TypeDescriptor strDesc = TypeDescriptor.valueOf(String.class);
         if (fieldDesc != null && this.conversionService.canConvert(fieldDesc, strDesc)) {
            return this.conversionService.convert(value, fieldDesc, strDesc);
         }
      }

      return value;
   }

   protected @Nullable PropertyEditor getCustomEditor(String fixedField) {
      Class<?> targetType = this.getPropertyAccessor().getPropertyType(fixedField);
      PropertyEditor editor = this.getPropertyAccessor().findCustomEditor(targetType, fixedField);
      if (editor == null) {
         editor = BeanUtils.findEditorByConvention(targetType);
      }

      return editor;
   }

   @Override
   public @Nullable PropertyEditor findEditor(@Nullable String field, @Nullable Class<?> valueType) {
      Class<?> valueTypeForLookup = valueType;
      if (valueTypeForLookup == null) {
         valueTypeForLookup = this.getFieldType(field);
      }

      PropertyEditor editor = super.findEditor(field, valueTypeForLookup);
      if (editor == null && this.conversionService != null) {
         TypeDescriptor td = null;
         if (field != null && this.getTarget() != null) {
            TypeDescriptor ptd = this.getPropertyAccessor().getPropertyTypeDescriptor(this.fixedField(field));
            if (ptd != null && (valueType == null || valueType.isAssignableFrom(ptd.getType()))) {
               td = ptd;
            }
         }

         if (td == null) {
            td = TypeDescriptor.valueOf(valueTypeForLookup);
         }

         if (this.conversionService.canConvert(TypeDescriptor.valueOf(String.class), td)) {
            editor = new ConvertingPropertyEditorAdapter(this.conversionService, td);
         }
      }

      return editor;
   }

   public abstract ConfigurablePropertyAccessor getPropertyAccessor();
}
