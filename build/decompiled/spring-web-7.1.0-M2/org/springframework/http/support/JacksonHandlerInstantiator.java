package org.springframework.http.support;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdResolver;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.util.Assert;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.HandlerInstantiator;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.jsontype.TypeIdResolver;
import tools.jackson.databind.jsontype.TypeResolverBuilder;
import tools.jackson.databind.ser.VirtualBeanPropertyWriter;
import tools.jackson.databind.util.Converter;

public class JacksonHandlerInstantiator extends HandlerInstantiator {
   private final AutowireCapableBeanFactory beanFactory;

   public JacksonHandlerInstantiator(AutowireCapableBeanFactory beanFactory) {
      Assert.notNull(beanFactory, "BeanFactory must not be null");
      this.beanFactory = beanFactory;
   }

   public @Nullable ValueDeserializer<?> deserializerInstance(DeserializationConfig config, Annotated annotated, Class<?> deserClass) {
      return (ValueDeserializer<?>)this.beanFactory.createBean(deserClass);
   }

   public KeyDeserializer keyDeserializerInstance(DeserializationConfig config, Annotated annotated, Class<?> keyDeserClass) {
      return (KeyDeserializer)this.beanFactory.createBean(keyDeserClass);
   }

   public ValueSerializer<?> serializerInstance(SerializationConfig config, Annotated annotated, Class<?> serClass) {
      return (ValueSerializer<?>)this.beanFactory.createBean(serClass);
   }

   public TypeResolverBuilder<?> typeResolverBuilderInstance(MapperConfig<?> config, Annotated annotated, Class<?> builderClass) {
      return (TypeResolverBuilder<?>)this.beanFactory.createBean(builderClass);
   }

   public TypeIdResolver typeIdResolverInstance(MapperConfig<?> config, Annotated annotated, Class<?> resolverClass) {
      return (TypeIdResolver)this.beanFactory.createBean(resolverClass);
   }

   public ValueInstantiator valueInstantiatorInstance(MapperConfig<?> config, Annotated annotated, Class<?> implClass) {
      return (ValueInstantiator)this.beanFactory.createBean(implClass);
   }

   public ObjectIdGenerator<?> objectIdGeneratorInstance(MapperConfig<?> config, Annotated annotated, Class<?> implClass) {
      return (ObjectIdGenerator<?>)this.beanFactory.createBean(implClass);
   }

   public ObjectIdResolver resolverIdGeneratorInstance(MapperConfig<?> config, Annotated annotated, Class<?> implClass) {
      return (ObjectIdResolver)this.beanFactory.createBean(implClass);
   }

   public PropertyNamingStrategy namingStrategyInstance(MapperConfig<?> config, Annotated annotated, Class<?> implClass) {
      return (PropertyNamingStrategy)this.beanFactory.createBean(implClass);
   }

   public Converter<?, ?> converterInstance(MapperConfig<?> config, Annotated annotated, Class<?> implClass) {
      return (Converter<?, ?>)this.beanFactory.createBean(implClass);
   }

   public VirtualBeanPropertyWriter virtualPropertyWriterInstance(MapperConfig<?> config, Class<?> implClass) {
      return (VirtualBeanPropertyWriter)this.beanFactory.createBean(implClass);
   }
}
