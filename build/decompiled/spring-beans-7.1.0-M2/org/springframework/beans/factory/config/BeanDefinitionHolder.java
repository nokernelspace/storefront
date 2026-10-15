package org.springframework.beans.factory.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class BeanDefinitionHolder implements BeanMetadataElement {
   private final BeanDefinition beanDefinition;
   private final String beanName;
   private final String @Nullable [] aliases;

   public BeanDefinitionHolder(BeanDefinition beanDefinition, String beanName) {
      this(beanDefinition, beanName, null);
   }

   public BeanDefinitionHolder(BeanDefinition beanDefinition, String beanName, String @Nullable [] aliases) {
      Assert.notNull(beanDefinition, "BeanDefinition must not be null");
      Assert.notNull(beanName, "Bean name must not be null");
      this.beanDefinition = beanDefinition;
      this.beanName = beanName;
      this.aliases = aliases;
   }

   public BeanDefinitionHolder(BeanDefinitionHolder beanDefinitionHolder) {
      Assert.notNull(beanDefinitionHolder, "BeanDefinitionHolder must not be null");
      this.beanDefinition = beanDefinitionHolder.getBeanDefinition();
      this.beanName = beanDefinitionHolder.getBeanName();
      this.aliases = beanDefinitionHolder.getAliases();
   }

   public BeanDefinition getBeanDefinition() {
      return this.beanDefinition;
   }

   public String getBeanName() {
      return this.beanName;
   }

   public String @Nullable [] getAliases() {
      return this.aliases;
   }

   @Override
   public @Nullable Object getSource() {
      return this.beanDefinition.getSource();
   }

   public boolean matchesName(@Nullable String candidateName) {
      return candidateName != null
         && (
            candidateName.equals(this.beanName)
               || candidateName.equals(BeanFactoryUtils.transformedBeanName(this.beanName))
               || ObjectUtils.containsElement(this.aliases, candidateName)
         );
   }

   public String getShortDescription() {
      return this.aliases == null
         ? "Bean definition with name '" + this.beanName + "'"
         : "Bean definition with name '" + this.beanName + "' and aliases [" + StringUtils.arrayToCommaDelimitedString(this.aliases) + "]";
   }

   public String getLongDescription() {
      return this.getShortDescription() + ": " + this.beanDefinition;
   }

   @Override
   public String toString() {
      return this.getLongDescription();
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof BeanDefinitionHolder that
            && this.beanDefinition.equals(that.beanDefinition)
            && this.beanName.equals(that.beanName)
            && ObjectUtils.nullSafeEquals(this.aliases, that.aliases);
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHash(new Object[]{this.beanDefinition, this.beanName, this.aliases});
   }
}
