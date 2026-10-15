package org.springframework.web.service.registry;

import java.util.Set;

public interface HttpServiceGroup {
   String DEFAULT_GROUP_NAME = "default";

   String name();

   Set<Class<?>> httpServiceTypes();

   HttpServiceGroup.ClientType clientType();

   enum ClientType {
      REST_CLIENT,
      WEB_CLIENT,
      UNSPECIFIED;

      boolean isUnspecified() {
         return this == UNSPECIFIED;
      }
   }
}
