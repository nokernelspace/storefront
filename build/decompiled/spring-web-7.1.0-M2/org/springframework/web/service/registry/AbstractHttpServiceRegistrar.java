package org.springframework.web.service.registry;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.service.annotation.HttpExchange;

public abstract class AbstractHttpServiceRegistrar
   implements ImportBeanDefinitionRegistrar,
   EnvironmentAware,
   ResourceLoaderAware,
   BeanFactoryAware,
   BeanClassLoaderAware {
   public static final String HTTP_SERVICE_PROXY_REGISTRY_BEAN_NAME = "httpServiceProxyRegistry";
   static final String HTTP_SERVICE_GROUP_NAME_ATTRIBUTE = "httpServiceGroupName";
   private HttpServiceGroup.ClientType defaultClientType = HttpServiceGroup.ClientType.UNSPECIFIED;
   private @Nullable Environment environment;
   private @Nullable ResourceLoader resourceLoader;
   private @Nullable BeanFactory beanFactory;
   private @Nullable ClassLoader beanClassLoader;
   private final GroupsMetadata groupsMetadata = new GroupsMetadata();
   private @Nullable ClassPathScanningCandidateComponentProvider scanner;

   public void setDefaultClientType(HttpServiceGroup.ClientType defaultClientType) {
      this.defaultClientType = defaultClientType;
   }

   public void setEnvironment(Environment environment) {
      this.environment = environment;
   }

   public void setResourceLoader(ResourceLoader resourceLoader) {
      this.resourceLoader = resourceLoader;
   }

   public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
      this.beanFactory = beanFactory;
   }

   public void setBeanClassLoader(ClassLoader beanClassLoader) {
      this.beanClassLoader = beanClassLoader;
   }

   public final void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry, BeanNameGenerator generator) {
      this.registerBeanDefinitions(metadata, registry);
   }

   public final void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry beanRegistry) {
      this.registerHttpServices(new AbstractHttpServiceRegistrar.DefaultGroupRegistry(), metadata);
      if (!this.groupsMetadata.isEmpty()) {
         RootBeanDefinition proxyRegistryBeanDef = this.createOrGetRegistry(beanRegistry);
         this.mergeGroups(proxyRegistryBeanDef);
         this.groupsMetadata.forEachRegistration((groupName, types) -> types.forEach(type -> {
            RootBeanDefinition proxyBeanDef = new RootBeanDefinition();
            proxyBeanDef.setBeanClassName(type);
            proxyBeanDef.setAttribute("httpServiceGroupName", groupName);
            proxyBeanDef.setInstanceSupplier(() -> this.getProxyInstance(groupName, type));
            String beanName = groupName + "#" + type;
            if (!beanRegistry.containsBeanDefinition(beanName)) {
               beanRegistry.registerBeanDefinition(beanName, proxyBeanDef);
            }
         }));
      }
   }

   protected abstract void registerHttpServices(AbstractHttpServiceRegistrar.GroupRegistry registry, AnnotationMetadata importingClassMetadata);

   private RootBeanDefinition createOrGetRegistry(BeanDefinitionRegistry beanRegistry) {
      if (!beanRegistry.containsBeanDefinition("httpServiceProxyRegistry")) {
         RootBeanDefinition proxyRegistryBeanDef = new RootBeanDefinition();
         proxyRegistryBeanDef.setBeanClass(HttpServiceProxyRegistryFactoryBean.class);
         ConstructorArgumentValues args = proxyRegistryBeanDef.getConstructorArgumentValues();
         args.addIndexedArgumentValue(0, new GroupsMetadata());
         beanRegistry.registerBeanDefinition("httpServiceProxyRegistry", proxyRegistryBeanDef);
         return proxyRegistryBeanDef;
      } else {
         return (RootBeanDefinition)beanRegistry.getBeanDefinition("httpServiceProxyRegistry");
      }
   }

   private void mergeGroups(RootBeanDefinition proxyRegistryBeanDef) {
      ConstructorArgumentValues args = proxyRegistryBeanDef.getConstructorArgumentValues();
      ValueHolder valueHolder = args.getArgumentValue(0, GroupsMetadata.class);
      Assert.state(valueHolder != null, "Expected GroupsMetadata constructor argument at index 0");
      GroupsMetadata target = (GroupsMetadata)valueHolder.getValue();
      Assert.state(target != null, "No constructor argument value");
      target.mergeWith(this.groupsMetadata);
   }

   private Object getProxyInstance(String groupName, String httpServiceType) {
      Assert.state(this.beanFactory != null, "BeanFactory has not been set");
      HttpServiceProxyRegistry registry = (HttpServiceProxyRegistry)this.beanFactory.getBean("httpServiceProxyRegistry", HttpServiceProxyRegistry.class);
      return registry.getClient(groupName, ClassUtils.resolveClassName(httpServiceType, this.beanClassLoader));
   }

   private Stream<BeanDefinition> findHttpServices(String basePackage) {
      if (this.scanner == null) {
         Assert.state(this.environment != null, "Environment has not been set");
         Assert.state(this.resourceLoader != null, "ResourceLoader has not been set");
         this.scanner = new AbstractHttpServiceRegistrar.HttpExchangeClassPathScanningCandidateComponentProvider();
         this.scanner.setEnvironment(this.environment);
         this.scanner.setResourceLoader(this.resourceLoader);
      }

      return this.scanner.findCandidateComponents(basePackage).stream();
   }

   private class DefaultGroupRegistry implements AbstractHttpServiceRegistrar.GroupRegistry {
      @Override
      public AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec forGroup(String name, HttpServiceGroup.ClientType clientType) {
         return new AbstractHttpServiceRegistrar.DefaultGroupRegistry.DefaultGroupSpec(name, clientType);
      }

      private class DefaultGroupSpec implements AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec {
         private final GroupsMetadata.Registration registration;

         DefaultGroupSpec(String groupName, HttpServiceGroup.ClientType clientType) {
            clientType = clientType != HttpServiceGroup.ClientType.UNSPECIFIED ? clientType : AbstractHttpServiceRegistrar.this.defaultClientType;
            this.registration = AbstractHttpServiceRegistrar.this.groupsMetadata.getOrCreateGroup(groupName, clientType);
         }

         @Override
         public AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec register(Class<?>... serviceTypes) {
            Arrays.stream(serviceTypes).map(Class::getName).forEach(this::registerServiceTypeName);
            return this;
         }

         @Override
         public AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec registerTypeNames(String... serviceTypes) {
            Arrays.stream(serviceTypes).forEach(this::registerServiceTypeName);
            return this;
         }

         @Override
         public AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec detectInBasePackages(Class<?>... packageClasses) {
            Arrays.stream(packageClasses).map(Class::getPackageName).forEach(this::detectInBasePackage);
            return this;
         }

         @Override
         public AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec detectInBasePackages(String... packageNames) {
            Arrays.stream(packageNames).forEach(this::detectInBasePackage);
            return this;
         }

         private void detectInBasePackage(String packageName) {
            AbstractHttpServiceRegistrar.this.findHttpServices(packageName)
               .<String>map(BeanDefinition::getBeanClassName)
               .filter(Objects::nonNull)
               .forEach(this::registerServiceTypeName);
         }

         private void registerServiceTypeName(String httpServiceTypeName) {
            this.registration.httpServiceTypeNames().add(httpServiceTypeName);
         }
      }
   }

   protected interface GroupRegistry {
      default AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec forGroup(String name) {
         return this.forGroup(name, HttpServiceGroup.ClientType.UNSPECIFIED);
      }

      AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec forGroup(String name, HttpServiceGroup.ClientType clientType);

      default AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec forDefaultGroup() {
         return this.forGroup("default");
      }

      interface GroupSpec {
         AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec register(Class<?>... serviceTypes);

         AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec registerTypeNames(String... serviceTypes);

         AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec detectInBasePackages(Class<?>... packageClasses);

         AbstractHttpServiceRegistrar.GroupRegistry.GroupSpec detectInBasePackages(String... packageNames);
      }
   }

   private static class HttpExchangeClassPathScanningCandidateComponentProvider extends ClassPathScanningCandidateComponentProvider {
      public HttpExchangeClassPathScanningCandidateComponentProvider() {
         this.addIncludeFilter(new AbstractHttpServiceRegistrar.HttpExchangeClassPathScanningCandidateComponentProvider.HttpExchangeFilter());
      }

      protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
         AnnotationMetadata metadata = beanDefinition.getMetadata();
         return metadata.isIndependent() && !metadata.isAnnotation();
      }

      private static class HttpExchangeFilter extends AnnotationTypeFilter {
         public HttpExchangeFilter() {
            super(HttpExchange.class, true, true);
         }

         protected boolean matchSelf(MetadataReader metadataReader) {
            if (metadataReader.getClassMetadata().isInterface()) {
               for (MethodMetadata metadata : metadataReader.getAnnotationMetadata().getDeclaredMethods()) {
                  if (metadata.getAnnotations().isPresent(HttpExchange.class)) {
                     return true;
                  }
               }
            }

            return false;
         }
      }
   }
}
