package org.springframework.beans.factory.parsing;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanReference;

public class BeanComponentDefinition extends BeanDefinitionHolder implements ComponentDefinition {
   private static final BeanDefinition[] EMPTY_BEAN_DEFINITION_ARRAY = new BeanDefinition[0];
   private static final BeanReference[] EMPTY_BEAN_REFERENCE_ARRAY = new BeanReference[0];
   private final BeanDefinition[] innerBeanDefinitions;
   private final BeanReference[] beanReferences;

   public BeanComponentDefinition(BeanDefinition beanDefinition, String beanName) {
      this(new BeanDefinitionHolder(beanDefinition, beanName));
   }

   public BeanComponentDefinition(BeanDefinition beanDefinition, String beanName, String @Nullable [] aliases) {
      this(new BeanDefinitionHolder(beanDefinition, beanName, aliases));
   }

   public BeanComponentDefinition(BeanDefinitionHolder beanDefinitionHolder) {
      super(beanDefinitionHolder);
      List<BeanDefinition> innerBeans = new ArrayList<>();
      List<BeanReference> references = new ArrayList<>();
      PropertyValues propertyValues = beanDefinitionHolder.getBeanDefinition().getPropertyValues();

      for (PropertyValue propertyValue : propertyValues.getPropertyValues()) {
         Object value = propertyValue.getValue();
         if (value instanceof BeanDefinitionHolder beanDefHolder) {
            innerBeans.add(beanDefHolder.getBeanDefinition());
         } else if (value instanceof BeanDefinition beanDef) {
            innerBeans.add(beanDef);
         } else if (value instanceof BeanReference beanRef) {
            references.add(beanRef);
         }
      }

      this.innerBeanDefinitions = innerBeans.toArray(EMPTY_BEAN_DEFINITION_ARRAY);
      this.beanReferences = references.toArray(EMPTY_BEAN_REFERENCE_ARRAY);
   }

   @Override
   public String getName() {
      return this.getBeanName();
   }

   @Override
   public String getDescription() {
      return this.getShortDescription();
   }

   @Override
   public BeanDefinition[] getBeanDefinitions() {
      return new BeanDefinition[]{this.getBeanDefinition()};
   }

   @Override
   public BeanDefinition[] getInnerBeanDefinitions() {
      return this.innerBeanDefinitions;
   }

   @Override
   public BeanReference[] getBeanReferences() {
      return this.beanReferences;
   }

   @Override
   public String toString() {
      return this.getDescription();
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof BeanComponentDefinition && super.equals(other);
   }
}
