package org.springframework.validation.beanvalidation;

import jakarta.validation.ClockProvider;
import jakarta.validation.Configuration;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidationProviderResolver;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorContext;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.bootstrap.GenericBootstrap;
import jakarta.validation.bootstrap.ProviderSpecificBootstrap;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.MessageSource;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

public class LocalValidatorFactoryBean extends SpringValidatorAdapter implements ValidatorFactory, ApplicationContextAware, InitializingBean, DisposableBean {
   private @Nullable Class providerClass;
   private @Nullable ValidationProviderResolver validationProviderResolver;
   private @Nullable MessageInterpolator messageInterpolator;
   private @Nullable TraversableResolver traversableResolver;
   private @Nullable ConstraintValidatorFactory constraintValidatorFactory;
   private @Nullable ParameterNameDiscoverer parameterNameDiscoverer;
   private Resource @Nullable [] mappingLocations;
   private final Map<String, String> validationPropertyMap = new HashMap<>();
   private @Nullable Consumer<Configuration<?>> configurationInitializer;
   private @Nullable ApplicationContext applicationContext;
   private @Nullable ValidatorFactory validatorFactory;

   public void setProviderClass(Class providerClass) {
      this.providerClass = providerClass;
   }

   public void setValidationProviderResolver(ValidationProviderResolver validationProviderResolver) {
      this.validationProviderResolver = validationProviderResolver;
   }

   public void setMessageInterpolator(MessageInterpolator messageInterpolator) {
      this.messageInterpolator = messageInterpolator;
   }

   public void setValidationMessageSource(MessageSource messageSource) {
      this.messageInterpolator = LocalValidatorFactoryBean.HibernateValidatorDelegate.buildMessageInterpolator(messageSource);
   }

   public void setTraversableResolver(TraversableResolver traversableResolver) {
      this.traversableResolver = traversableResolver;
   }

   public void setConstraintValidatorFactory(ConstraintValidatorFactory constraintValidatorFactory) {
      this.constraintValidatorFactory = constraintValidatorFactory;
   }

   public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
      this.parameterNameDiscoverer = parameterNameDiscoverer;
   }

   public void setMappingLocations(Resource... mappingLocations) {
      this.mappingLocations = mappingLocations;
   }

   public void setValidationProperties(Properties jpaProperties) {
      CollectionUtils.mergePropertiesIntoMap(jpaProperties, this.validationPropertyMap);
   }

   public void setValidationPropertyMap(@Nullable Map<String, String> validationProperties) {
      if (validationProperties != null) {
         this.validationPropertyMap.putAll(validationProperties);
      }
   }

   public Map<String, String> getValidationPropertyMap() {
      return this.validationPropertyMap;
   }

   public void setConfigurationInitializer(Consumer<Configuration<?>> configurationInitializer) {
      this.configurationInitializer = configurationInitializer;
   }

   @Override
   public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   public void afterPropertiesSet() {
      Configuration<?> configuration;
      if (this.providerClass != null) {
         ProviderSpecificBootstrap bootstrap = Validation.byProvider(this.providerClass);
         if (this.validationProviderResolver != null) {
            bootstrap = bootstrap.providerResolver(this.validationProviderResolver);
         }

         configuration = bootstrap.configure();
      } else {
         GenericBootstrap bootstrap = Validation.byDefaultProvider();
         if (this.validationProviderResolver != null) {
            bootstrap = bootstrap.providerResolver(this.validationProviderResolver);
         }

         configuration = bootstrap.configure();
      }

      if (this.applicationContext != null) {
         try {
            Method eclMethod = configuration.getClass().getMethod("externalClassLoader", ClassLoader.class);
            eclMethod = ClassUtils.getPubliclyAccessibleMethodIfPossible(eclMethod, configuration.getClass());
            ReflectionUtils.invokeMethod(eclMethod, configuration, new Object[]{this.applicationContext.getClassLoader()});
         } catch (NoSuchMethodException var16) {
         }
      }

      MessageInterpolator targetInterpolator = this.messageInterpolator;
      if (targetInterpolator == null) {
         targetInterpolator = configuration.getDefaultMessageInterpolator();
      }

      configuration.messageInterpolator(new LocaleContextMessageInterpolator(targetInterpolator));
      if (this.traversableResolver != null) {
         configuration.traversableResolver(this.traversableResolver);
      }

      ConstraintValidatorFactory targetConstraintValidatorFactory = this.constraintValidatorFactory;
      if (targetConstraintValidatorFactory == null && this.applicationContext != null) {
         targetConstraintValidatorFactory = new SpringConstraintValidatorFactory(
            this.applicationContext.getAutowireCapableBeanFactory(), configuration.getDefaultConstraintValidatorFactory()
         );
      }

      if (targetConstraintValidatorFactory != null) {
         configuration.constraintValidatorFactory(targetConstraintValidatorFactory);
      }

      if (this.parameterNameDiscoverer != null) {
         this.configureParameterNameProvider(this.parameterNameDiscoverer, configuration);
      }

      List<InputStream> mappingStreams = null;
      if (this.mappingLocations != null) {
         mappingStreams = new ArrayList<>(this.mappingLocations.length);

         for (Resource location : this.mappingLocations) {
            try {
               InputStream stream = location.getInputStream();
               mappingStreams.add(stream);
               configuration.addMapping(stream);
            } catch (IOException ex) {
               this.closeMappingStreams(mappingStreams);
               throw new IllegalStateException("Cannot read mapping resource: " + location);
            }
         }
      }

      this.validationPropertyMap.forEach(configuration::addProperty);
      if (this.configurationInitializer != null) {
         this.configurationInitializer.accept(configuration);
      }

      this.postProcessConfiguration(configuration);

      try {
         this.validatorFactory = configuration.buildValidatorFactory();
         this.setTargetValidator(this.validatorFactory.getValidator());
      } finally {
         this.closeMappingStreams(mappingStreams);
      }
   }

   private void configureParameterNameProvider(ParameterNameDiscoverer discoverer, Configuration<?> configuration) {
      final ParameterNameProvider defaultProvider = configuration.getDefaultParameterNameProvider();
      configuration.parameterNameProvider(new ParameterNameProvider() {
         public List<String> getParameterNames(Constructor<?> constructor) {
            String[] paramNames = discoverer.getParameterNames(constructor);
            return paramNames != null ? Arrays.asList(paramNames) : defaultProvider.getParameterNames(constructor);
         }

         public List<String> getParameterNames(Method method) {
            String[] paramNames = discoverer.getParameterNames(method);
            return paramNames != null ? Arrays.asList(paramNames) : defaultProvider.getParameterNames(method);
         }
      });
   }

   private void closeMappingStreams(@Nullable List<InputStream> mappingStreams) {
      if (!CollectionUtils.isEmpty(mappingStreams)) {
         for (InputStream stream : mappingStreams) {
            try {
               stream.close();
            } catch (IOException var5) {
            }
         }
      }
   }

   protected void postProcessConfiguration(Configuration<?> configuration) {
   }

   private ValidatorFactory obtainValidatorFactory() {
      Assert.state(this.validatorFactory != null, "No target ValidatorFactory set");
      return this.validatorFactory;
   }

   public Validator getValidator() {
      return this.obtainValidatorFactory().getValidator();
   }

   public ValidatorContext usingContext() {
      return this.obtainValidatorFactory().usingContext();
   }

   public MessageInterpolator getMessageInterpolator() {
      return this.obtainValidatorFactory().getMessageInterpolator();
   }

   public TraversableResolver getTraversableResolver() {
      return this.obtainValidatorFactory().getTraversableResolver();
   }

   public ConstraintValidatorFactory getConstraintValidatorFactory() {
      return this.obtainValidatorFactory().getConstraintValidatorFactory();
   }

   public ParameterNameProvider getParameterNameProvider() {
      return this.obtainValidatorFactory().getParameterNameProvider();
   }

   public ClockProvider getClockProvider() {
      return this.obtainValidatorFactory().getClockProvider();
   }

   @Override
   public <T> T unwrap(@Nullable Class<T> type) {
      if (type == null || !ValidatorFactory.class.isAssignableFrom(type)) {
         try {
            return super.unwrap(type);
         } catch (ValidationException var4) {
         }
      }

      if (this.validatorFactory != null) {
         try {
            return (T)this.validatorFactory.unwrap(type);
         } catch (ValidationException ex) {
            if (ValidatorFactory.class == type) {
               return (T)this.validatorFactory;
            } else {
               throw ex;
            }
         }
      } else {
         throw new ValidationException("Cannot unwrap to " + type);
      }
   }

   public void close() {
      if (this.validatorFactory != null) {
         this.validatorFactory.close();
      }
   }

   public void destroy() {
      this.close();
   }

   private static class HibernateValidatorDelegate {
      public static MessageInterpolator buildMessageInterpolator(MessageSource messageSource) {
         return new ResourceBundleMessageInterpolator(new MessageSourceResourceBundleLocator(messageSource));
      }
   }
}
