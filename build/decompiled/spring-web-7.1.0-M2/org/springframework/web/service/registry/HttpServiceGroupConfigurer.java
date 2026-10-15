package org.springframework.web.service.registry;

import java.util.function.Predicate;
import org.springframework.core.Ordered;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@FunctionalInterface
public interface HttpServiceGroupConfigurer<CB> extends Ordered {
   void configureGroups(HttpServiceGroupConfigurer.Groups<CB> groups);

   default int getOrder() {
      return Integer.MAX_VALUE;
   }

   @FunctionalInterface
   interface ClientCallback<CB> {
      void withClient(HttpServiceGroup group, CB clientBuilder);
   }

   @FunctionalInterface
   interface GroupCallback<CB> {
      void withGroup(HttpServiceGroup group, CB clientBuilder, HttpServiceProxyFactory.Builder factoryBuilder);
   }

   interface Groups<CB> {
      HttpServiceGroupConfigurer.Groups<CB> filterByName(String... groupNames);

      HttpServiceGroupConfigurer.Groups<CB> filter(Predicate<HttpServiceGroup> predicate);

      void forEachClient(HttpServiceGroupConfigurer.ClientCallback<CB> callback);

      void forEachClient(HttpServiceGroupConfigurer.InitializingClientCallback<CB> callback);

      void forEachProxyFactory(HttpServiceGroupConfigurer.ProxyFactoryCallback callback);

      void forEachGroup(HttpServiceGroupConfigurer.GroupCallback<CB> callback);
   }

   @FunctionalInterface
   interface InitializingClientCallback<CB> {
      CB initClient(HttpServiceGroup group);
   }

   @FunctionalInterface
   interface ProxyFactoryCallback {
      void withProxyFactory(HttpServiceGroup group, HttpServiceProxyFactory.Builder factoryBuilder);
   }
}
