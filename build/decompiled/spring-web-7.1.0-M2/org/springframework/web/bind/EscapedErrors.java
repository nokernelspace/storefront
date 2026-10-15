package org.springframework.web.bind;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.util.HtmlUtils;

public class EscapedErrors implements Errors {
   private final Errors source;

   public EscapedErrors(Errors source) {
      Assert.notNull(source, "Errors source must not be null");
      this.source = source;
   }

   public Errors getSource() {
      return this.source;
   }

   public String getObjectName() {
      return this.source.getObjectName();
   }

   public void setNestedPath(String nestedPath) {
      this.source.setNestedPath(nestedPath);
   }

   public String getNestedPath() {
      return this.source.getNestedPath();
   }

   public void pushNestedPath(String subPath) {
      this.source.pushNestedPath(subPath);
   }

   public void popNestedPath() throws IllegalStateException {
      this.source.popNestedPath();
   }

   public void reject(String errorCode) {
      this.source.reject(errorCode);
   }

   public void reject(String errorCode, String defaultMessage) {
      this.source.reject(errorCode, defaultMessage);
   }

   public void reject(String errorCode, Object @Nullable [] errorArgs, @Nullable String defaultMessage) {
      this.source.reject(errorCode, errorArgs, defaultMessage);
   }

   public void rejectValue(@Nullable String field, String errorCode) {
      this.source.rejectValue(field, errorCode);
   }

   public void rejectValue(@Nullable String field, String errorCode, String defaultMessage) {
      this.source.rejectValue(field, errorCode, defaultMessage);
   }

   public void rejectValue(@Nullable String field, String errorCode, Object @Nullable [] errorArgs, @Nullable String defaultMessage) {
      this.source.rejectValue(field, errorCode, errorArgs, defaultMessage);
   }

   public void addAllErrors(Errors errors) {
      this.source.addAllErrors(errors);
   }

   public boolean hasErrors() {
      return this.source.hasErrors();
   }

   public int getErrorCount() {
      return this.source.getErrorCount();
   }

   public List<ObjectError> getAllErrors() {
      return this.escapeObjectErrors(this.source.getAllErrors());
   }

   public boolean hasGlobalErrors() {
      return this.source.hasGlobalErrors();
   }

   public int getGlobalErrorCount() {
      return this.source.getGlobalErrorCount();
   }

   public List<ObjectError> getGlobalErrors() {
      return this.escapeObjectErrors(this.source.getGlobalErrors());
   }

   public @Nullable ObjectError getGlobalError() {
      return this.escapeObjectError(this.source.getGlobalError());
   }

   public boolean hasFieldErrors() {
      return this.source.hasFieldErrors();
   }

   public int getFieldErrorCount() {
      return this.source.getFieldErrorCount();
   }

   public List<FieldError> getFieldErrors() {
      return this.escapeObjectErrors(this.source.getFieldErrors());
   }

   public @Nullable FieldError getFieldError() {
      return this.escapeObjectError(this.source.getFieldError());
   }

   public boolean hasFieldErrors(String field) {
      return this.source.hasFieldErrors(field);
   }

   public int getFieldErrorCount(String field) {
      return this.source.getFieldErrorCount(field);
   }

   public List<FieldError> getFieldErrors(String field) {
      return this.escapeObjectErrors(this.source.getFieldErrors(field));
   }

   public @Nullable FieldError getFieldError(String field) {
      return this.escapeObjectError(this.source.getFieldError(field));
   }

   public @Nullable Object getFieldValue(String field) {
      Object value = this.source.getFieldValue(field);
      return value instanceof String text ? HtmlUtils.htmlEscape(text) : value;
   }

   public @Nullable Class<?> getFieldType(String field) {
      return this.source.getFieldType(field);
   }

   private <T extends ObjectError> @Nullable T escapeObjectError(@Nullable T source) {
      if (source == null) {
         return null;
      }

      String defaultMessage = source.getDefaultMessage();
      if (defaultMessage != null) {
         defaultMessage = HtmlUtils.htmlEscape(defaultMessage);
      }

      if (source instanceof FieldError fieldError) {
         Object value = fieldError.getRejectedValue();
         if (value instanceof String text) {
            value = HtmlUtils.htmlEscape(text);
         }

         return (T)(new FieldError(
            fieldError.getObjectName(),
            fieldError.getField(),
            value,
            fieldError.isBindingFailure(),
            fieldError.getCodes(),
            fieldError.getArguments(),
            defaultMessage
         ));
      } else {
         return (T)(new ObjectError(source.getObjectName(), source.getCodes(), source.getArguments(), defaultMessage));
      }
   }

   private <T extends ObjectError> List<T> escapeObjectErrors(List<T> source) {
      List<T> escaped = new ArrayList<>(source.size());

      for (T objectError : source) {
         escaped.add(this.escapeObjectError(objectError));
      }

      return escaped;
   }
}
