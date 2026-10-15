package org.springframework.validation;

import java.beans.PropertyEditor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public abstract class AbstractBindingResult extends AbstractErrors implements BindingResult, Serializable {
   private final String objectName;
   private MessageCodesResolver messageCodesResolver = new DefaultMessageCodesResolver();
   private final List<ObjectError> errors = new ArrayList<>();
   private final Map<String, Class<?>> fieldTypes = new HashMap<>();
   private final Map<String, Object> fieldValues = new HashMap<>();
   private final Set<String> suppressedFields = new HashSet<>();

   protected AbstractBindingResult(String objectName) {
      this.objectName = objectName;
   }

   public void setMessageCodesResolver(MessageCodesResolver messageCodesResolver) {
      Assert.notNull(messageCodesResolver, "MessageCodesResolver must not be null");
      this.messageCodesResolver = messageCodesResolver;
   }

   public MessageCodesResolver getMessageCodesResolver() {
      return this.messageCodesResolver;
   }

   @Override
   public String getObjectName() {
      return this.objectName;
   }

   @Override
   public void reject(String errorCode, Object @Nullable [] errorArgs, @Nullable String defaultMessage) {
      this.addError(new ObjectError(this.getObjectName(), this.resolveMessageCodes(errorCode), errorArgs, defaultMessage));
   }

   @Override
   public void rejectValue(@Nullable String field, String errorCode, Object @Nullable [] errorArgs, @Nullable String defaultMessage) {
      if (!StringUtils.hasLength(this.getNestedPath()) && !StringUtils.hasLength(field)) {
         this.reject(errorCode, errorArgs, defaultMessage);
      } else {
         String fixedField = this.fixedField(field);
         Object newVal = this.getActualFieldValue(fixedField);
         FieldError fe = new FieldError(this.getObjectName(), fixedField, newVal, false, this.resolveMessageCodes(errorCode, field), errorArgs, defaultMessage);
         this.addError(fe);
      }
   }

   @Override
   public void addAllErrors(Errors errors) {
      if (!errors.getObjectName().equals(this.getObjectName())) {
         throw new IllegalArgumentException("Errors object needs to have same object name");
      }

      this.errors.addAll(errors.getAllErrors());
   }

   @Override
   public boolean hasErrors() {
      return !this.errors.isEmpty();
   }

   @Override
   public int getErrorCount() {
      return this.errors.size();
   }

   @Override
   public List<ObjectError> getAllErrors() {
      return Collections.unmodifiableList(this.errors);
   }

   @Override
   public List<ObjectError> getGlobalErrors() {
      List<ObjectError> result = new ArrayList<>();

      for (ObjectError objectError : this.errors) {
         if (!(objectError instanceof FieldError)) {
            result.add(objectError);
         }
      }

      return Collections.unmodifiableList(result);
   }

   @Override
   public @Nullable ObjectError getGlobalError() {
      for (ObjectError objectError : this.errors) {
         if (!(objectError instanceof FieldError)) {
            return objectError;
         }
      }

      return null;
   }

   @Override
   public List<FieldError> getFieldErrors() {
      List<FieldError> result = new ArrayList<>();

      for (ObjectError objectError : this.errors) {
         if (objectError instanceof FieldError fieldError) {
            result.add(fieldError);
         }
      }

      return Collections.unmodifiableList(result);
   }

   @Override
   public @Nullable FieldError getFieldError() {
      for (ObjectError objectError : this.errors) {
         if (objectError instanceof FieldError fieldError) {
            return fieldError;
         }
      }

      return null;
   }

   @Override
   public List<FieldError> getFieldErrors(String field) {
      List<FieldError> result = new ArrayList<>();
      String fixedField = this.fixedField(field);

      for (ObjectError objectError : this.errors) {
         if (objectError instanceof FieldError fieldError && this.isMatchingFieldError(fixedField, fieldError)) {
            result.add(fieldError);
         }
      }

      return Collections.unmodifiableList(result);
   }

   @Override
   public @Nullable FieldError getFieldError(String field) {
      String fixedField = this.fixedField(field);

      for (ObjectError objectError : this.errors) {
         if (objectError instanceof FieldError fieldError && this.isMatchingFieldError(fixedField, fieldError)) {
            return fieldError;
         }
      }

      return null;
   }

   @Override
   public @Nullable Object getFieldValue(String field) {
      FieldError fieldError = this.getFieldError(field);
      if (fieldError == null) {
         if (this.getTarget() != null) {
            Object value = this.getActualFieldValue(this.fixedField(field));
            return this.formatFieldValue(field, value);
         } else {
            return this.fieldValues.get(field);
         }
      } else {
         Object value = fieldError.getRejectedValue();
         return !fieldError.isBindingFailure() && this.getTarget() != null ? this.formatFieldValue(field, value) : value;
      }
   }

   @Override
   public @Nullable Class<?> getFieldType(@Nullable String field) {
      if (this.getTarget() != null) {
         Object value = this.getActualFieldValue(this.fixedField(field));
         if (value != null) {
            return value.getClass();
         }
      }

      return this.fieldTypes.get(field);
   }

   @Override
   public Map<String, Object> getModel() {
      Map<String, Object> model = new LinkedHashMap<>(2);
      model.put(this.getObjectName(), this.getTarget());
      model.put(MODEL_KEY_PREFIX + this.getObjectName(), this);
      return model;
   }

   @Override
   public @Nullable Object getRawFieldValue(String field) {
      return this.getTarget() != null ? this.getActualFieldValue(this.fixedField(field)) : null;
   }

   @Override
   public @Nullable PropertyEditor findEditor(@Nullable String field, @Nullable Class<?> valueType) {
      PropertyEditorRegistry editorRegistry = this.getPropertyEditorRegistry();
      if (editorRegistry != null) {
         Class<?> valueTypeToUse = valueType;
         if (valueTypeToUse == null) {
            valueTypeToUse = this.getFieldType(field);
         }

         return editorRegistry.findCustomEditor(valueTypeToUse, this.fixedField(field));
      } else {
         return null;
      }
   }

   @Override
   public @Nullable PropertyEditorRegistry getPropertyEditorRegistry() {
      return null;
   }

   @Override
   public String[] resolveMessageCodes(String errorCode) {
      return this.getMessageCodesResolver().resolveMessageCodes(errorCode, this.getObjectName());
   }

   @Override
   public String[] resolveMessageCodes(String errorCode, @Nullable String field) {
      return this.getMessageCodesResolver().resolveMessageCodes(errorCode, this.getObjectName(), this.fixedField(field), this.getFieldType(field));
   }

   @Override
   public void addError(ObjectError error) {
      this.errors.add(error);
   }

   @Override
   public void recordFieldValue(String field, Class<?> type, @Nullable Object value) {
      this.fieldTypes.put(field, type);
      this.fieldValues.put(field, value);
   }

   @Override
   public void recordSuppressedField(String field) {
      this.suppressedFields.add(field);
   }

   @Override
   public String[] getSuppressedFields() {
      return StringUtils.toStringArray(this.suppressedFields);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof BindingResult that
            && this.getObjectName().equals(that.getObjectName())
            && ObjectUtils.nullSafeEquals(this.getTarget(), that.getTarget())
            && this.getAllErrors().equals(that.getAllErrors());
   }

   @Override
   public int hashCode() {
      return this.getObjectName().hashCode();
   }

   @Override
   public abstract @Nullable Object getTarget();

   protected abstract @Nullable Object getActualFieldValue(String field);

   protected @Nullable Object formatFieldValue(String field, @Nullable Object value) {
      return value;
   }
}
