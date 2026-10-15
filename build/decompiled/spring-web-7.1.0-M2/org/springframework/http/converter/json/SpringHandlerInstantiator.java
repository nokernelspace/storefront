package org.springframework.http.converter.json;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdResolver;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.ser.VirtualBeanPropertyWriter;
import com.fasterxml.jackson.databind.util.Converter;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.util.Assert;

@Deprecated(since = "7.0", forRemoval = true)
public class SpringHandlerInstantiator extends HandlerInstantiator {
   private final AutowireCapableBeanFactory beanFactory;

   public SpringHandlerInstantiator(AutowireCapableBeanFactory beanFactory) {
      Assert.notNull(beanFactory, "BeanFactory must not be null");
      this.beanFactory = beanFactory;
   }

   public JsonDeserializer<?> deserializerInstance(DeserializationConfig config, Annotated annotated, Class<?> implClass) {
      return (JsonDeserializer<?>)this.beanFactory.createBean(implClass);
   }

   public KeyDeserializer keyDeserializerInstance(DeserializationConfig config, Annotated annotated, Class<?> implClass) {
      return (KeyDeserializer)this.beanFactory.createBean(implClass);
   }

   public JsonSerializer<?> serializerInstance(SerializationConfig config, Annotated annotated, Class<?> implClass) {
      return (JsonSerializer<?>)this.beanFactory.createBean(implClass);
   }

   public TypeResolverBuilder<?> typeResolverBuilderInstance(MapperConfig<?> config, Annotated annotated, Class<?> implClass) {
      return (TypeResolverBuilder<?>)this.beanFactory.createBean(implClass);
   }

   public TypeIdResolver typeIdResolverInstance(MapperConfig<?> config, Annotated annotated, Class<?> implClass) {
      return (TypeIdResolver)this.beanFactory.createBean(implClass);
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
