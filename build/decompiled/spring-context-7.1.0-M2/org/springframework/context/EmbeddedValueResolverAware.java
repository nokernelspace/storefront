package org.springframework.context;

import org.springframework.beans.factory.Aware;
import org.springframework.util.StringValueResolver;

public interface EmbeddedValueResolverAware extends Aware {
   void setEmbeddedValueResolver(StringValueResolver resolver);
}
