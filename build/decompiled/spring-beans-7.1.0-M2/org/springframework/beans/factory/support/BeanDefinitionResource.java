package org.springframework.beans.factory.support;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.io.AbstractResource;
import org.springframework.util.Assert;

class BeanDefinitionResource extends AbstractResource {
   private final BeanDefinition beanDefinition;

   public BeanDefinitionResource(BeanDefinition beanDefinition) {
      Assert.notNull(beanDefinition, "BeanDefinition must not be null");
      this.beanDefinition = beanDefinition;
   }

   public final BeanDefinition getBeanDefinition() {
      return this.beanDefinition;
   }

   public boolean exists() {
      return false;
   }

   public boolean isReadable() {
      return false;
   }

   public InputStream getInputStream() throws IOException {
      throw new FileNotFoundException("Resource cannot be opened because it points to " + this.getDescription());
   }

   public String getDescription() {
      return "BeanDefinition defined in " + this.beanDefinition.getResourceDescription();
   }

   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof BeanDefinitionResource that && this.beanDefinition.equals(that.beanDefinition);
   }

   public int hashCode() {
      return this.beanDefinition.hashCode();
   }
}
