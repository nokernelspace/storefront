package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import org.jspecify.annotations.Nullable;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;

public abstract class AdviceModeImportSelector<A extends Annotation> implements ImportSelector {
   public static final String DEFAULT_ADVICE_MODE_ATTRIBUTE_NAME = "mode";

   protected String getAdviceModeAttributeName() {
      return "mode";
   }

   @Override
   public final String[] selectImports(AnnotationMetadata importingClassMetadata) {
      Class<?> annType = GenericTypeResolver.resolveTypeArgument(this.getClass(), AdviceModeImportSelector.class);
      Assert.state(annType != null, "Unresolvable type argument for AdviceModeImportSelector");
      AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(importingClassMetadata, annType);
      if (attributes == null) {
         throw new IllegalArgumentException(
            String.format("@%s is not present on importing class '%s' as expected", annType.getSimpleName(), importingClassMetadata.getClassName())
         );
      } else {
         AdviceMode adviceMode = (AdviceMode)attributes.getEnum(this.getAdviceModeAttributeName());
         String[] imports = this.selectImports(adviceMode);
         if (imports == null) {
            throw new IllegalArgumentException("Unknown AdviceMode: " + adviceMode);
         } else {
            return imports;
         }
      }
   }

   protected abstract String @Nullable [] selectImports(AdviceMode adviceMode);
}
