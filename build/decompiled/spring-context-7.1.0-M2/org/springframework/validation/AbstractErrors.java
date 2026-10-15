package org.springframework.validation;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public abstract class AbstractErrors implements Errors, Serializable {
   private String nestedPath = "";
   private final Deque<String> nestedPathStack = new ArrayDeque<>();

   @Override
   public void setNestedPath(@Nullable String nestedPath) {
      this.doSetNestedPath(nestedPath);
      this.nestedPathStack.clear();
   }

   @Override
   public String getNestedPath() {
      return this.nestedPath;
   }

   @Override
   public void pushNestedPath(String subPath) {
      this.nestedPathStack.push(this.getNestedPath());
      this.doSetNestedPath(this.getNestedPath() + subPath);
   }

   @Override
   public void popNestedPath() throws IllegalStateException {
      try {
         String formerNestedPath = this.nestedPathStack.pop();
         this.doSetNestedPath(formerNestedPath);
      } catch (NoSuchElementException ex) {
         throw new IllegalStateException("Cannot pop nested path: no nested path on stack");
      }
   }

   protected void doSetNestedPath(@Nullable String nestedPath) {
      if (nestedPath == null) {
         nestedPath = "";
      }

      nestedPath = this.canonicalFieldName(nestedPath);
      if (!nestedPath.isEmpty() && !nestedPath.endsWith(".")) {
         nestedPath = nestedPath + ".";
      }

      this.nestedPath = nestedPath;
   }

   protected String fixedField(@Nullable String field) {
      if (StringUtils.hasLength(field)) {
         return this.getNestedPath() + this.canonicalFieldName(field);
      }

      String path = this.getNestedPath();
      return path.endsWith(".") ? path.substring(0, path.length() - ".".length()) : path;
   }

   protected String canonicalFieldName(String field) {
      return field;
   }

   @Override
   public List<FieldError> getFieldErrors(String field) {
      List<FieldError> fieldErrors = this.getFieldErrors();
      List<FieldError> result = new ArrayList<>();
      String fixedField = this.fixedField(field);

      for (FieldError fieldError : fieldErrors) {
         if (this.isMatchingFieldError(fixedField, fieldError)) {
            result.add(fieldError);
         }
      }

      return Collections.unmodifiableList(result);
   }

   protected boolean isMatchingFieldError(String field, FieldError fieldError) {
      if (field.equals(fieldError.getField())) {
         return true;
      }

      int endIndex = field.length() - 1;
      return endIndex >= 0 && field.charAt(endIndex) == '*' && (endIndex == 0 || field.regionMatches(0, fieldError.getField(), 0, endIndex));
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder(this.getClass().getName());
      sb.append(": ").append(this.getErrorCount()).append(" errors");

      for (ObjectError error : this.getAllErrors()) {
         sb.append('\n').append(error);
      }

      return sb.toString();
   }
}
