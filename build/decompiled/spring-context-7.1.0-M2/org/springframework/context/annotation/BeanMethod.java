package org.springframework.context.annotation;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.core.type.MethodMetadata;

final class BeanMethod extends ConfigurationMethod {
   BeanMethod(MethodMetadata metadata, ConfigurationClass configurationClass) {
      super(metadata, configurationClass);
   }

   @Override
   public void validate(ProblemReporter problemReporter) {
      if (this.getMetadata().getAnnotationAttributes(Autowired.class.getName()) != null) {
         problemReporter.error(new BeanMethod.AutowiredDeclaredMethodError());
      }

      if ("void".equals(this.getMetadata().getReturnTypeName())) {
         problemReporter.error(new BeanMethod.VoidDeclaredMethodError());
      }

      if (!this.getMetadata().isStatic()) {
         Map<String, Object> attributes = this.getConfigurationClass().getMetadata().getAnnotationAttributes(Configuration.class.getName());
         if (attributes != null && (Boolean)attributes.get("proxyBeanMethods") && !this.getMetadata().isOverridable()) {
            problemReporter.error(new BeanMethod.NonOverridableMethodError());
         }
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof BeanMethod that
            && this.configurationClass.equals(that.configurationClass)
            && getLocalMethodIdentifier(this.metadata).equals(getLocalMethodIdentifier(that.metadata));
   }

   @Override
   public int hashCode() {
      return this.configurationClass.hashCode() * 31 + getLocalMethodIdentifier(this.metadata).hashCode();
   }

   @Override
   public String toString() {
      return "BeanMethod: " + this.metadata;
   }

   private static String getLocalMethodIdentifier(MethodMetadata metadata) {
      String metadataString = metadata.toString();
      int index = metadataString.indexOf(metadata.getDeclaringClassName());
      return index >= 0 ? metadataString.substring(index + metadata.getDeclaringClassName().length()) : metadataString;
   }

   private class AutowiredDeclaredMethodError extends Problem {
      AutowiredDeclaredMethodError() {
         super(
            "@Bean method '%s' must not be declared as autowired; remove the method-level @Autowired annotation."
               .formatted(BeanMethod.this.getMetadata().getMethodName()),
            BeanMethod.this.getResourceLocation()
         );
      }
   }

   private class NonOverridableMethodError extends Problem {
      NonOverridableMethodError() {
         super(
            "@Bean method '%s' must not be private or final; change the method's modifiers to continue."
               .formatted(BeanMethod.this.getMetadata().getMethodName()),
            BeanMethod.this.getResourceLocation()
         );
      }
   }

   private class VoidDeclaredMethodError extends Problem {
      VoidDeclaredMethodError() {
         super(
            "@Bean method '%s' must not be declared as void; change the method's return type or its annotation."
               .formatted(BeanMethod.this.getMetadata().getMethodName()),
            BeanMethod.this.getResourceLocation()
         );
      }
   }
}
