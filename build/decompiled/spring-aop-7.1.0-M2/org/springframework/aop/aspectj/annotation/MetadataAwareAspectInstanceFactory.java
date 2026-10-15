package org.springframework.aop.aspectj.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.aspectj.AspectInstanceFactory;

public interface MetadataAwareAspectInstanceFactory extends AspectInstanceFactory {
   AspectMetadata getAspectMetadata();

   @Nullable Object getAspectCreationMutex();
}
