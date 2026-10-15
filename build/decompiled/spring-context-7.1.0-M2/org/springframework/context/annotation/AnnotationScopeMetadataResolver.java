package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.Assert;

public class AnnotationScopeMetadataResolver implements ScopeMetadataResolver {
   private final ScopedProxyMode defaultProxyMode;
   protected Class<? extends Annotation> scopeAnnotationType = Scope.class;

   public AnnotationScopeMetadataResolver() {
      this.defaultProxyMode = ScopedProxyMode.NO;
   }

   public AnnotationScopeMetadataResolver(ScopedProxyMode defaultProxyMode) {
      Assert.notNull(defaultProxyMode, "'defaultProxyMode' must not be null");
      this.defaultProxyMode = defaultProxyMode;
   }

   public void setScopeAnnotationType(Class<? extends Annotation> scopeAnnotationType) {
      Assert.notNull(scopeAnnotationType, "'scopeAnnotationType' must not be null");
      this.scopeAnnotationType = scopeAnnotationType;
   }

   @Override
   public ScopeMetadata resolveScopeMetadata(BeanDefinition definition) {
      ScopeMetadata metadata = new ScopeMetadata();
      if (definition instanceof AnnotatedBeanDefinition annDef) {
         AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(annDef.getMetadata(), this.scopeAnnotationType);
         if (attributes != null) {
            metadata.setScopeName(attributes.getString("value"));
            ScopedProxyMode proxyMode = (ScopedProxyMode)attributes.getEnum("proxyMode");
            if (proxyMode == ScopedProxyMode.DEFAULT) {
               proxyMode = this.defaultProxyMode;
            }

            metadata.setScopedProxyMode(proxyMode);
         }
      }

      return metadata;
   }
}
