package org.springframework.beans.factory.parsing;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.util.Assert;

public class AliasDefinition implements BeanMetadataElement {
   private final String beanName;
   private final String alias;
   private final @Nullable Object source;

   public AliasDefinition(String beanName, String alias) {
      this(beanName, alias, null);
   }

   public AliasDefinition(String beanName, String alias, @Nullable Object source) {
      Assert.notNull(beanName, "Bean name must not be null");
      Assert.notNull(alias, "Alias must not be null");
      this.beanName = beanName;
      this.alias = alias;
      this.source = source;
   }

   public final String getBeanName() {
      return this.beanName;
   }

   public final String getAlias() {
      return this.alias;
   }

   @Override
   public final @Nullable Object getSource() {
      return this.source;
   }
}
