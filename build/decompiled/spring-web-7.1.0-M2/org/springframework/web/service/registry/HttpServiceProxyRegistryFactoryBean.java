package org.springframework.web.service.registry;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringValueResolver;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public final class HttpServiceProxyRegistryFactoryBean
   implements ApplicationContextAware,
   BeanClassLoaderAware,
   EmbeddedValueResolverAware,
   InitializingBean,
   FactoryBean<HttpServiceProxyRegistry> {
   private static final Map<HttpServiceGroup.ClientType, HttpServiceGroupAdapter<?>> groupAdapters = HttpServiceProxyRegistryFactoryBean.GroupAdapterInitializer.initGroupAdapters();
   private final GroupsMetadata groupsMetadata;
   private @Nullable ApplicationContext applicationContext;
   private @Nullable ClassLoader beanClassLoader;
   private @Nullable HttpServiceProxyRegistry proxyRegistry;
   private @Nullable StringValueResolver embeddedValueResolver;

   HttpServiceProxyRegistryFactoryBean(GroupsMetadata groupsMetadata) {
      this.groupsMetadata = groupsMetadata;
   }

   public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
      this.applicationContext = applicationContext;
   }

   public void setBeanClassLoader(ClassLoader beanClassLoader) {
      this.beanClassLoader = beanClassLoader;
   }

   public void setEmbeddedValueResolver(StringValueResolver resolver) {
      this.embeddedValueResolver = resolver;
   }

   public Class<?> getObjectType() {
      return HttpServiceProxyRegistry.class;
   }

   public void afterPropertiesSet() {
      Assert.notNull(this.applicationContext, "ApplicationContext not initialized");
      Assert.notNull(this.beanClassLoader, "BeanClassLoader not initialized");
      Set<HttpServiceProxyRegistryFactoryBean.ConfigurableGroup> groups = this.groupsMetadata
         .groups(this.beanClassLoader)
         .stream()
         .map(group -> new HttpServiceProxyRegistryFactoryBean.ConfigurableGroup(group, this.embeddedValueResolver))
         .collect(Collectors.toSet());
      groupAdapters.forEach(
         (clientType, groupAdapter) -> this.applicationContext
            .getBeanProvider(groupAdapter.getConfigurerType())
            .orderedStream()
            .forEach(configurer -> configurer.configureGroups(new HttpServiceProxyRegistryFactoryBean.DefaultGroups(groups, clientType)))
      );
      Map<String, Map<Class<?>, Object>> proxies = groups.stream()
         .collect(
            Collectors.toMap(HttpServiceProxyRegistryFactoryBean.ConfigurableGroup::name, HttpServiceProxyRegistryFactoryBean.ConfigurableGroup::createProxies)
         );
      this.proxyRegistry = new HttpServiceProxyRegistryFactoryBean.DefaultHttpServiceProxyRegistry(proxies);
   }

   public HttpServiceProxyRegistry getObject() {
      Assert.state(this.proxyRegistry != null, "HttpServiceProxyRegistry not initialized");
      return this.proxyRegistry;
   }

   private static final class ConfigurableGroup {
      private final HttpServiceGroup group;
      private final HttpServiceGroupAdapter<?> groupAdapter;
      private @Nullable Object clientBuilder;
      private final HttpServiceProxyFactory.Builder proxyFactoryBuilder = HttpServiceProxyFactory.builder();

      ConfigurableGroup(HttpServiceGroup group, @Nullable StringValueResolver valueResolver) {
         this.group = group;
         this.groupAdapter = getGroupAdapter(group.clientType());
         if (valueResolver != null) {
            this.proxyFactoryBuilder.embeddedValueResolver(valueResolver);
         }
      }

      private static HttpServiceGroupAdapter<?> getGroupAdapter(HttpServiceGroup.ClientType clientType) {
         HttpServiceGroupAdapter<?> adapter = HttpServiceProxyRegistryFactoryBean.groupAdapters.get(clientType);
         Assert.state(adapter != null, "No HttpServiceGroupAdapter for type " + clientType);
         return adapter;
      }

      public String name() {
         return this.group.name();
      }

      HttpServiceGroup httpServiceGroup() {
         return this.group;
      }

      public <CB> void applyClientCallback(HttpServiceGroupConfigurer.ClientCallback<CB> callback) {
         callback.withClient(this.group, this.getClientBuilder());
      }

      public <CB> void applyClientCallback(HttpServiceGroupConfigurer.InitializingClientCallback<CB> callback) {
         Assert.state(this.clientBuilder == null, "Client builder already initialized");
         this.clientBuilder = callback.initClient(this.group);
      }

      public void applyProxyFactoryCallback(HttpServiceGroupConfigurer.ProxyFactoryCallback callback) {
         callback.withProxyFactory(this.group, this.proxyFactoryBuilder);
      }

      public <CB> void applyGroupCallback(HttpServiceGroupConfigurer.GroupCallback<CB> callback) {
         callback.withGroup(this.group, this.getClientBuilder(), this.proxyFactoryBuilder);
      }

      private <CB> CB getClientBuilder() {
         if (this.clientBuilder == null) {
            this.clientBuilder = this.groupAdapter.createClientBuilder();
         }

         return (CB)this.clientBuilder;
      }

      public Map<Class<?>, Object> createProxies() {
         Map<Class<?>, Object> map = new LinkedHashMap<>(this.group.httpServiceTypes().size());
         HttpExchangeAdapter adapter = ((HttpServiceGroupAdapter<Object>)this.groupAdapter).createExchangeAdapter(this.getClientBuilder());
         HttpServiceProxyFactory factory = this.proxyFactoryBuilder.exchangeAdapter(adapter).build();
         this.group.httpServiceTypes().forEach(type -> map.put((Class<?>)type, factory.createClient((Class<?>)type)));
         return map;
      }

      @Override
      public String toString() {
         return this.getClass().getSimpleName() + "[name=" + this.name() + "]";
      }
   }

   private static final class DefaultGroups<CB> implements HttpServiceGroupConfigurer.Groups<CB> {
      private final Set<HttpServiceProxyRegistryFactoryBean.ConfigurableGroup> groups;
      private final Predicate<HttpServiceProxyRegistryFactoryBean.ConfigurableGroup> clientTypeFilter;
      private Predicate<HttpServiceProxyRegistryFactoryBean.ConfigurableGroup> filter;

      DefaultGroups(Set<HttpServiceProxyRegistryFactoryBean.ConfigurableGroup> groups, HttpServiceGroup.ClientType clientType) {
         this.groups = groups;
         this.clientTypeFilter = group -> group.httpServiceGroup().clientType().equals(clientType);
         this.filter = this.clientTypeFilter;
      }

      @Override
      public HttpServiceGroupConfigurer.Groups<CB> filterByName(String... groupNames) {
         return this.filter(group -> Arrays.stream(groupNames).anyMatch(name -> name.equals(group.name())));
      }

      @Override
      public HttpServiceGroupConfigurer.Groups<CB> filter(Predicate<HttpServiceGroup> predicate) {
         this.filter = this.filter.and(group -> predicate.test(group.httpServiceGroup()));
         return this;
      }

      @Override
      public void forEachClient(HttpServiceGroupConfigurer.ClientCallback<CB> callback) {
         this.filterAndReset().forEach(group -> group.applyClientCallback(callback));
      }

      @Override
      public void forEachClient(HttpServiceGroupConfigurer.InitializingClientCallback<CB> callback) {
         this.filterAndReset().forEach(group -> group.applyClientCallback(callback));
      }

      @Override
      public void forEachProxyFactory(HttpServiceGroupConfigurer.ProxyFactoryCallback callback) {
         this.filterAndReset().forEach(group -> group.applyProxyFactoryCallback(callback));
      }

      @Override
      public void forEachGroup(HttpServiceGroupConfigurer.GroupCallback<CB> callback) {
         this.filterAndReset().forEach(group -> group.applyGroupCallback(callback));
      }

      private Stream<HttpServiceProxyRegistryFactoryBean.ConfigurableGroup> filterAndReset() {
         Stream<HttpServiceProxyRegistryFactoryBean.ConfigurableGroup> stream = this.groups.stream().filter(this.filter);
         this.filter = this.clientTypeFilter;
         return stream;
      }
   }

   private static final class DefaultHttpServiceProxyRegistry implements HttpServiceProxyRegistry {
      private final Map<String, Map<Class<?>, Object>> groupProxyMap;
      private final MultiValueMap<Class<?>, Object> directLookupMap;

      DefaultHttpServiceProxyRegistry(Map<String, Map<Class<?>, Object>> groupProxyMap) {
         this.groupProxyMap = groupProxyMap;
         this.directLookupMap = new LinkedMultiValueMap();
         groupProxyMap.values().forEach(map -> map.forEach(this.directLookupMap::add));
      }

      @Override
      public <P> P getClient(Class<P> type) {
         List<Object> map = (List<Object>)this.directLookupMap.getOrDefault(type, Collections.emptyList());
         Assert.notEmpty(map, "No client of type " + type.getName());
         Assert.isTrue(map.size() <= 1, "No unique client of type " + type.getName());
         return (P)map.get(0);
      }

      @Override
      public <P> P getClient(String groupName, Class<P> type) {
         Map<Class<?>, Object> map = this.getProxyMapForGroup(groupName);
         P proxy = (P)map.get(type);
         Assert.notNull(proxy, "No client of type " + type + " in group '" + groupName + "': " + map.keySet());
         return proxy;
      }

      @Override
      public Set<String> getGroupNames() {
         return this.groupProxyMap.keySet();
      }

      @Override
      public Set<Class<?>> getClientTypesInGroup(String groupName) {
         return this.getProxyMapForGroup(groupName).keySet();
      }

      private Map<Class<?>, Object> getProxyMapForGroup(String groupName) {
         Map<Class<?>, Object> map = this.groupProxyMap.get(groupName);
         Assert.notNull(map, "No group with name '" + groupName + "'");
         return map;
      }
   }

   private static class GroupAdapterInitializer {
      private static final String REST_CLIENT_HTTP_SERVICE_GROUP_ADAPTER = "org.springframework.web.client.support.RestClientHttpServiceGroupAdapter";
      private static final String WEB_CLIENT_HTTP_SERVICE_GROUP_ADAPTER = "org.springframework.web.reactive.function.client.support.WebClientHttpServiceGroupAdapter";

      static Map<HttpServiceGroup.ClientType, HttpServiceGroupAdapter<?>> initGroupAdapters() {
         Map<HttpServiceGroup.ClientType, HttpServiceGroupAdapter<?>> map = new LinkedHashMap<>(2);
         addGroupAdapter(map, HttpServiceGroup.ClientType.REST_CLIENT, "org.springframework.web.client.support.RestClientHttpServiceGroupAdapter");
         addGroupAdapter(
            map, HttpServiceGroup.ClientType.WEB_CLIENT, "org.springframework.web.reactive.function.client.support.WebClientHttpServiceGroupAdapter"
         );
         return map;
      }

      private static void addGroupAdapter(
         Map<HttpServiceGroup.ClientType, HttpServiceGroupAdapter<?>> groupAdapters, HttpServiceGroup.ClientType clientType, String className
      ) {
         try {
            Class<?> clazz = ClassUtils.forName(className, HttpServiceGroupAdapter.class.getClassLoader());
            groupAdapters.put(clientType, (HttpServiceGroupAdapter<?>)BeanUtils.instantiateClass(clazz));
         } catch (ClassNotFoundException var4) {
         }
      }
   }

   static class HttpServiceProxyRegistryRuntimeHints implements RuntimeHintsRegistrar {
      public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
         hints.reflection()
            .registerType(
               TypeReference.of("org.springframework.web.client.support.RestClientHttpServiceGroupAdapter"),
               new MemberCategory[]{MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS}
            )
            .registerTypeIfPresent(
               classLoader,
               "org.springframework.web.reactive.function.client.support.WebClientHttpServiceGroupAdapter",
               new MemberCategory[]{MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS}
            );
      }
   }
}
