package org.springframework.validation;

import org.springframework.beans.PropertyAccessException;

public interface BindingErrorProcessor {
   void processMissingFieldError(String missingField, BindingResult bindingResult);

   void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult);
}
