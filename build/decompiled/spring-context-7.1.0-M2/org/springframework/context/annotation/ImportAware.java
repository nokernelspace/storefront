package org.springframework.context.annotation;

import org.springframework.beans.factory.Aware;
import org.springframework.core.type.AnnotationMetadata;

public interface ImportAware extends Aware {
   void setImportMetadata(AnnotationMetadata importMetadata);
}
