package org.springframework.beans.factory.aot;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class AotBeanProcessingException extends AotProcessingException {
   private final RootBeanDefinition beanDefinition;

   public AotBeanProcessingException(RegisteredBean registeredBean, String msg, @Nullable Throwable cause) {
      super(createErrorMessage(registeredBean, msg), cause);
      this.beanDefinition = registeredBean.getMergedBeanDefinition();
   }

   public AotBeanProcessingException(RegisteredBean registeredBean, String msg) {
      this(registeredBean, msg, null);
   }

   private static String createErrorMessage(RegisteredBean registeredBean, String msg) {
      StringBuilder sb = new StringBuilder("Error processing bean with name '");
      sb.append(registeredBean.getBeanName()).append("'");
      String resourceDescription = registeredBean.getMergedBeanDefinition().getResourceDescription();
      if (resourceDescription != null) {
         sb.append(" defined in ").append(resourceDescription);
      }

      sb.append(": ").append(msg);
      return sb.toString();
   }

   public RootBeanDefinition getBeanDefinition() {
      return this.beanDefinition;
   }
}
