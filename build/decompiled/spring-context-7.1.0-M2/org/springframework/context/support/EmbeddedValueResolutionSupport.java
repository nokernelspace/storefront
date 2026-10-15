package org.springframework.context.support;

import org.jspecify.annotations.Nullable;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.util.StringValueResolver;

public class EmbeddedValueResolutionSupport implements EmbeddedValueResolverAware {
   private @Nullable StringValueResolver embeddedValueResolver;

   @Override
   public void setEmbeddedValueResolver(StringValueResolver resolver) {
      this.embeddedValueResolver = resolver;
   }

   protected @Nullable String resolveEmbeddedValue(String value) {
      return this.embeddedValueResolver != null ? this.embeddedValueResolver.resolveStringValue(value) : value;
   }
}
