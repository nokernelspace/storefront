package org.springframework.validation;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public interface Errors {
   String NESTED_PATH_SEPARATOR = ".";

   String getObjectName();

   default void setNestedPath(String nestedPath) {
      throw new UnsupportedOperationException(this.getClass().getSimpleName() + " does not support nested paths");
   }

   default String getNestedPath() {
      return "";
   }

   default void pushNestedPath(String subPath) {
      throw new UnsupportedOperationException(this.getClass().getSimpleName() + " does not support nested paths");
   }

   default void popNestedPath() throws IllegalStateException {
      throw new IllegalStateException("Cannot pop nested path: no nested path on stack");
   }

   default void reject(String errorCode) {
      this.reject(errorCode, null, null);
   }

   default void reject(String errorCode, String defaultMessage) {
      this.reject(errorCode, null, defaultMessage);
   }

   void reject(String errorCode, Object @Nullable [] errorArgs, @Nullable String defaultMessage);

   default void rejectValue(@Nullable String field, String errorCode) {
      this.rejectValue(field, errorCode, null, null);
   }

   default void rejectValue(@Nullable String field, String errorCode, String defaultMessage) {
      this.rejectValue(field, errorCode, null, defaultMessage);
   }

   void rejectValue(@Nullable String field, String errorCode, Object @Nullable [] errorArgs, @Nullable String defaultMessage);

   default void addAllErrors(Errors errors) {
      throw new UnsupportedOperationException(this.getClass().getSimpleName() + " does not support addAllErrors");
   }

   default <T extends Throwable> void failOnError(Function<String, T> messageToException) throws T {
      if (this.hasErrors()) {
         throw messageToException.apply(this.toString());
      }
   }

   default boolean hasErrors() {
      return !this.getGlobalErrors().isEmpty() || !this.getFieldErrors().isEmpty();
   }

   default int getErrorCount() {
      return this.getGlobalErrors().size() + this.getFieldErrors().size();
   }

   default List<ObjectError> getAllErrors() {
      return Stream.concat(this.getGlobalErrors().stream(), this.getFieldErrors().stream()).toList();
   }

   default boolean hasGlobalErrors() {
      return !this.getGlobalErrors().isEmpty();
   }

   default int getGlobalErrorCount() {
      return this.getGlobalErrors().size();
   }

   List<ObjectError> getGlobalErrors();

   default @Nullable ObjectError getGlobalError() {
      return this.getGlobalErrors().stream().findFirst().orElse(null);
   }

   default boolean hasFieldErrors() {
      return !this.getFieldErrors().isEmpty();
   }

   default int getFieldErrorCount() {
      return this.getFieldErrors().size();
   }

   List<FieldError> getFieldErrors();

   default @Nullable FieldError getFieldError() {
      return this.getFieldErrors().stream().findFirst().orElse(null);
   }

   default boolean hasFieldErrors(String field) {
      return this.getFieldError(field) != null;
   }

   default int getFieldErrorCount(String field) {
      return this.getFieldErrors(field).size();
   }

   default List<FieldError> getFieldErrors(String field) {
      return this.getFieldErrors().stream().filter(error -> field.equals(error.getField())).toList();
   }

   default @Nullable FieldError getFieldError(String field) {
      return this.getFieldErrors().stream().filter(error -> field.equals(error.getField())).findFirst().orElse(null);
   }

   @Nullable Object getFieldValue(String field);

   default @Nullable Class<?> getFieldType(String field) {
      return Optional.ofNullable(this.getFieldValue(field)).map(Object::getClass).orElse(null);
   }

   @Override
   String toString();
}
