package org.springframework.validation.beanvalidation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.Path.Node;
import jakarta.validation.executable.ExecutableValidator;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.NotReadablePropertyException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.SmartValidator;

public class SpringValidatorAdapter implements SmartValidator, Validator {
   private static final Set<String> INTERNAL_ANNOTATION_ATTRIBUTES = Set.of("message", "groups", "payload");
   private @Nullable Validator targetValidator;

   public SpringValidatorAdapter(Validator targetValidator) {
      Assert.notNull(targetValidator, "Target Validator must not be null");
      this.targetValidator = targetValidator;
   }

   SpringValidatorAdapter() {
   }

   void setTargetValidator(Validator targetValidator) {
      this.targetValidator = targetValidator;
   }

   @Override
   public boolean supports(Class<?> clazz) {
      return this.targetValidator != null;
   }

   @Override
   public void validate(Object target, Errors errors) {
      if (this.targetValidator != null) {
         this.processConstraintViolations(this.targetValidator.validate(target, new Class[0]), errors);
      }
   }

   @Override
   public void validate(Object target, Errors errors, Object... validationHints) {
      if (this.targetValidator != null) {
         this.processConstraintViolations(this.targetValidator.validate(target, this.asValidationGroups(validationHints)), errors);
      }
   }

   @Override
   public void validateValue(Class<?> targetType, @Nullable String fieldName, @Nullable Object value, Errors errors, Object... validationHints) {
      if (this.targetValidator != null) {
         this.processConstraintViolations(this.targetValidator.validateValue(targetType, fieldName, value, this.asValidationGroups(validationHints)), errors);
      }
   }

   private Class<?>[] asValidationGroups(Object... validationHints) {
      Set<Class<?>> groups = new LinkedHashSet<>(4);

      for (Object hint : validationHints) {
         if (hint instanceof Class<?> clazz) {
            groups.add(clazz);
         }
      }

      return ClassUtils.toClassArray(groups);
   }

   protected void processConstraintViolations(Set<ConstraintViolation<Object>> violations, Errors errors) {
      Set<String> failedFields = new HashSet<>();
      if (errors.hasErrors()) {
         for (ObjectError error : errors.getAllErrors()) {
            if (error instanceof FieldError fieldError && fieldError.isBindingFailure()) {
               failedFields.add(fieldError.getField());
            }
         }
      }

      for (ConstraintViolation<Object> violation : violations) {
         String field = this.determineField(violation);
         if (!failedFields.contains(field)) {
            try {
               ConstraintDescriptor<?> cd = violation.getConstraintDescriptor();
               String errorCode = this.determineErrorCode(cd);
               Object[] errorArgs = this.getArgumentsForConstraint(errors.getObjectName(), field, cd);
               if (errors instanceof BindingResult bindingResult) {
                  String nestedField = bindingResult.getNestedPath() + field;
                  if (nestedField.isEmpty()) {
                     String[] errorCodes = bindingResult.resolveMessageCodes(errorCode);
                     ObjectError error = new SpringValidatorAdapter.ValidationObjectError(errors.getObjectName(), errorCodes, errorArgs, violation, this);
                     bindingResult.addError(error);
                  } else {
                     Object rejectedValue = this.getRejectedValue(field, violation, bindingResult);
                     String[] errorCodes = bindingResult.resolveMessageCodes(errorCode, field);
                     FieldError error = new SpringValidatorAdapter.ValidationFieldError(
                        errors.getObjectName(), nestedField, rejectedValue, errorCodes, errorArgs, violation, this
                     );
                     bindingResult.addError(error);
                  }
               } else {
                  errors.rejectValue(field, errorCode, errorArgs, violation.getMessage());
               }
            } catch (NotReadablePropertyException ex) {
               throw new IllegalStateException(
                  "JSR-303 validated property '"
                     + field
                     + "' does not have a corresponding accessor for Spring data binding - check your DataBinder's configuration (bean property versus direct field access)",
                  ex
               );
            }
         }
      }
   }

   protected String determineField(ConstraintViolation<Object> violation) {
      Path path = violation.getPropertyPath();
      StringBuilder sb = new StringBuilder();
      boolean first = true;

      for (Node node : path) {
         if (node.isInIterable() && !first) {
            sb.append('[');
            Object index = node.getIndex();
            if (index == null) {
               index = node.getKey();
            }

            if (index != null) {
               sb.append(index);
            }

            sb.append(']');
         }

         String name = node.getName();
         if (name != null && node.getKind() == ElementKind.PROPERTY && !name.startsWith("<")) {
            if (!first) {
               sb.append('.');
            }

            first = false;
            sb.append(name);
         }
      }

      return sb.toString();
   }

   protected String determineErrorCode(ConstraintDescriptor<?> descriptor) {
      return descriptor.getAnnotation().annotationType().getSimpleName();
   }

   protected Object[] getArgumentsForConstraint(String objectName, String field, ConstraintDescriptor<?> descriptor) {
      List<Object> arguments = new ArrayList<>();
      arguments.add(this.getResolvableField(objectName, field));
      Map<String, Object> attributesToExpose = new TreeMap<>();
      descriptor.getAttributes().forEach((attributeName, attributeValue) -> {
         if (!INTERNAL_ANNOTATION_ATTRIBUTES.contains(attributeName)) {
            if (attributeValue instanceof String str) {
               attributeValue = new SpringValidatorAdapter.ResolvableAttribute(str);
            }

            attributesToExpose.put(attributeName, attributeValue);
         }
      });
      arguments.addAll(attributesToExpose.values());
      return arguments.toArray();
   }

   protected MessageSourceResolvable getResolvableField(String objectName, String field) {
      String[] codes = StringUtils.hasText(field) ? new String[]{objectName + "." + field, field} : new String[]{objectName};
      return new DefaultMessageSourceResolvable(codes, field);
   }

   protected @Nullable Object getRejectedValue(String field, ConstraintViolation<Object> violation, BindingResult bindingResult) {
      Object invalidValue = violation.getInvalidValue();
      if (!field.isEmpty() && !field.contains("[]") && (invalidValue == violation.getLeafBean() || field.contains("[") || field.contains("."))) {
         try {
            invalidValue = bindingResult.getRawFieldValue(field);
         } catch (InvalidPropertyException var6) {
         }
      }

      return invalidValue;
   }

   protected boolean requiresMessageFormat(ConstraintViolation<?> violation) {
      return containsSpringStylePlaceholder(violation.getMessage());
   }

   private static boolean containsSpringStylePlaceholder(@Nullable String message) {
      return message != null && message.contains("{0}");
   }

   public <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups) {
      Assert.state(this.targetValidator != null, "No target Validator set");
      return this.targetValidator.validate(object, groups);
   }

   public <T> Set<ConstraintViolation<T>> validateProperty(T object, String propertyName, Class<?>... groups) {
      Assert.state(this.targetValidator != null, "No target Validator set");
      return this.targetValidator.validateProperty(object, propertyName, groups);
   }

   public <T> Set<ConstraintViolation<T>> validateValue(Class<T> beanType, String propertyName, Object value, Class<?>... groups) {
      Assert.state(this.targetValidator != null, "No target Validator set");
      return this.targetValidator.validateValue(beanType, propertyName, value, groups);
   }

   public BeanDescriptor getConstraintsForClass(Class<?> clazz) {
      Assert.state(this.targetValidator != null, "No target Validator set");
      return this.targetValidator.getConstraintsForClass(clazz);
   }

   @Override
   public <T> T unwrap(@Nullable Class<T> type) {
      Assert.state(this.targetValidator != null, "No target Validator set");

      try {
         return (T)(type != null ? this.targetValidator.unwrap(type) : this.targetValidator);
      } catch (ValidationException ex) {
         if (Validator.class == type) {
            return (T)this.targetValidator;
         } else {
            throw ex;
         }
      }
   }

   public ExecutableValidator forExecutables() {
      Assert.state(this.targetValidator != null, "No target Validator set");
      return this.targetValidator.forExecutables();
   }

   private static class ResolvableAttribute implements MessageSourceResolvable, Serializable {
      private final String resolvableString;

      public ResolvableAttribute(String resolvableString) {
         this.resolvableString = resolvableString;
      }

      @Override
      public String[] getCodes() {
         return new String[]{this.resolvableString};
      }

      @Override
      public Object @Nullable [] getArguments() {
         return null;
      }

      @Override
      public String getDefaultMessage() {
         return this.resolvableString;
      }

      @Override
      public String toString() {
         return this.resolvableString;
      }
   }

   private static class ValidationFieldError extends FieldError implements Serializable {
      private transient @Nullable SpringValidatorAdapter adapter;
      private transient @Nullable ConstraintViolation<?> violation;

      public ValidationFieldError(
         String objectName,
         String field,
         @Nullable Object rejectedValue,
         String[] codes,
         Object[] arguments,
         ConstraintViolation<?> violation,
         SpringValidatorAdapter adapter
      ) {
         super(objectName, field, rejectedValue, false, codes, arguments, violation.getMessage());
         this.adapter = adapter;
         this.violation = violation;
         this.wrap(violation);
      }

      @Override
      public boolean shouldRenderDefaultMessage() {
         return this.adapter != null && this.violation != null
            ? this.adapter.requiresMessageFormat(this.violation)
            : SpringValidatorAdapter.containsSpringStylePlaceholder(this.getDefaultMessage());
      }
   }

   private static class ValidationObjectError extends ObjectError implements Serializable {
      private transient @Nullable SpringValidatorAdapter adapter;
      private transient @Nullable ConstraintViolation<?> violation;

      public ValidationObjectError(String objectName, String[] codes, Object[] arguments, ConstraintViolation<?> violation, SpringValidatorAdapter adapter) {
         super(objectName, codes, arguments, violation.getMessage());
         this.adapter = adapter;
         this.violation = violation;
         this.wrap(violation);
      }

      @Override
      public boolean shouldRenderDefaultMessage() {
         return this.adapter != null && this.violation != null
            ? this.adapter.requiresMessageFormat(this.violation)
            : SpringValidatorAdapter.containsSpringStylePlaceholder(this.getDefaultMessage());
      }
   }
}
