package org.springframework.validation.beanvalidation;

import jakarta.validation.ValidationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class OptionalValidatorFactoryBean extends LocalValidatorFactoryBean {
   @Override
   public void afterPropertiesSet() {
      try {
         super.afterPropertiesSet();
      } catch (ValidationException ex) {
         Log logger = LogFactory.getLog(this.getClass());
         if (logger.isDebugEnabled()) {
            logger.debug("Failed to set up a Bean Validation provider", ex);
         } else if (logger.isInfoEnabled()) {
            logger.info("Failed to set up a Bean Validation provider: " + ex);
         }
      }
   }
}
