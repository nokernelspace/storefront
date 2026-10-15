package org.springframework.validation;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.PropertyAccessException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class DefaultBindingErrorProcessor implements BindingErrorProcessor {
   public static final String MISSING_FIELD_ERROR_CODE = "required";

   @Override
   public void processMissingFieldError(String missingField, BindingResult bindingResult) {
      String fixedField = bindingResult.getNestedPath() + missingField;
      String[] codes = bindingResult.resolveMessageCodes("required", missingField);
      Object[] arguments = this.getArgumentsForBindError(bindingResult.getObjectName(), fixedField);
      bindingResult.addError(new DefaultBindingErrorProcessor.BindingFieldError(bindingResult.getObjectName(), fixedField, "", codes, arguments));
   }

   @Override
   public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult) {
      String field = ex.getPropertyName();
      Assert.state(field != null, "No field in exception");
      String[] codes = bindingResult.resolveMessageCodes(ex.getErrorCode(), field);
      Object[] arguments = this.getArgumentsForBindError(bindingResult.getObjectName(), field);
      Object rejectedValue = ex.getValue();
      if (ObjectUtils.isArray(rejectedValue)) {
         rejectedValue = StringUtils.arrayToCommaDelimitedString(ObjectUtils.toObjectArray(rejectedValue));
      }

      bindingResult.addError(new DefaultBindingErrorProcessor.BindingFieldError(bindingResult.getObjectName(), field, rejectedValue, codes, arguments, ex));
   }

   protected Object[] getArgumentsForBindError(String objectName, String field) {
      String[] codes = new String[]{objectName + "." + field, field};
      return new Object[]{new DefaultMessageSourceResolvable(codes, field)};
   }

   private static class BindingFieldError extends FieldError implements Serializable {
      public BindingFieldError(String objectName, String field, @Nullable Object rejectedValue, String[] codes, Object[] arguments) {
         super(objectName, field, rejectedValue, true, codes, arguments, "Field '" + field + "' is required");
      }

      public BindingFieldError(String objectName, String field, @Nullable Object rejectedValue, String[] codes, Object[] arguments, PropertyAccessException ex) {
         super(objectName, field, rejectedValue, true, codes, arguments, ex.getLocalizedMessage());
         this.wrap(ex);
      }

      @Override
      public boolean shouldRenderDefaultMessage() {
         return false;
      }
   }
}
