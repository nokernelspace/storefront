package org.springframework.boot.context;

import java.io.IOException;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

public class TypeExcludeFilter implements TypeFilter, BeanFactoryAware {
   private BeanFactory beanFactory;
   private @Nullable Collection<TypeExcludeFilter> delegates;

   public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
      this.beanFactory = beanFactory;
   }

   public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
      if (this.beanFactory instanceof ListableBeanFactory && this.getClass() == TypeExcludeFilter.class) {
         for (TypeExcludeFilter delegate : this.getDelegates()) {
            if (delegate.match(metadataReader, metadataReaderFactory)) {
               return true;
            }
         }
      }

      return false;
   }

   private Collection<TypeExcludeFilter> getDelegates() {
      Collection<TypeExcludeFilter> delegates = this.delegates;
      if (delegates == null) {
         delegates = ((ListableBeanFactory)this.beanFactory).getBeansOfType(TypeExcludeFilter.class).values();
         this.delegates = delegates;
      }

      return delegates;
   }

   @Override
   public boolean equals(@Nullable Object obj) {
      throw new IllegalStateException("TypeExcludeFilter " + this.getClass() + " has not implemented equals");
   }

   @Override
   public int hashCode() {
      throw new IllegalStateException("TypeExcludeFilter " + this.getClass() + " has not implemented hashCode");
   }
}
