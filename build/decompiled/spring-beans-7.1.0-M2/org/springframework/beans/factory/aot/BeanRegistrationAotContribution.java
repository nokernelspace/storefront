package org.springframework.beans.factory.aot;

import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.util.Assert;

@FunctionalInterface
public interface BeanRegistrationAotContribution {
   default BeanRegistrationCodeFragments customizeBeanRegistrationCodeFragments(
      GenerationContext generationContext, BeanRegistrationCodeFragments codeFragments
   ) {
      return codeFragments;
   }

   void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode);

   static BeanRegistrationAotContribution withCustomCodeFragments(UnaryOperator<BeanRegistrationCodeFragments> defaultCodeFragments) {
      Assert.notNull(defaultCodeFragments, "'defaultCodeFragments' must not be null");
      return new BeanRegistrationAotContribution() {
         @Override
         public BeanRegistrationCodeFragments customizeBeanRegistrationCodeFragments(
            GenerationContext generationContext, BeanRegistrationCodeFragments codeFragments
         ) {
            return defaultCodeFragments.apply(codeFragments);
         }

         @Override
         public void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
         }
      };
   }

   static @Nullable BeanRegistrationAotContribution concat(@Nullable BeanRegistrationAotContribution a, @Nullable BeanRegistrationAotContribution b) {
      if (a == null) {
         return b;
      } else {
         return b == null ? a : (generationContext, beanRegistrationCode) -> {
            a.applyTo(generationContext, beanRegistrationCode);
            b.applyTo(generationContext, beanRegistrationCode);
         };
      }
   }
}
