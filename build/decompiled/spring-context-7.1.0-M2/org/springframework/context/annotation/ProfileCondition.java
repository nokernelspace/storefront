package org.springframework.context.annotation;

import java.util.List;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;

class ProfileCondition implements Condition {
   @Override
   public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      MultiValueMap<String, Object> attrs = metadata.getAllAnnotationAttributes(Profile.class.getName());
      if (attrs != null) {
         for (Object value : (List)attrs.get("value")) {
            if (context.getEnvironment().matchesProfiles((String[])value)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }
}
