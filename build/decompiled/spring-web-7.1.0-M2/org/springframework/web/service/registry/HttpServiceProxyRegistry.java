package org.springframework.web.service.registry;

import java.util.Set;

public interface HttpServiceProxyRegistry {
   <P> P getClient(Class<P> httpServiceType);

   <P> P getClient(String groupName, Class<P> httpServiceType);

   Set<String> getGroupNames();

   Set<Class<?>> getClientTypesInGroup(String groupName);
}
