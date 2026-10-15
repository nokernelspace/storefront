package org.springframework.web.accept;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class ContentNegotiationManagerFactoryBean implements FactoryBean<ContentNegotiationManager>, InitializingBean {
   private @Nullable List<ContentNegotiationStrategy> strategies;
   private boolean favorParameter = false;
   private String parameterName = "format";
   private final Map<String, MediaType> mediaTypes = new HashMap<>();
   private @Nullable Boolean useRegisteredExtensionsOnly;
   private boolean ignoreAcceptHeader = false;
   private @Nullable ContentNegotiationStrategy defaultNegotiationStrategy;
   private @Nullable ContentNegotiationManager contentNegotiationManager;

   public void setStrategies(@Nullable List<ContentNegotiationStrategy> strategies) {
      this.strategies = strategies != null ? new ArrayList<>(strategies) : null;
   }

   public void setFavorParameter(boolean favorParameter) {
      this.favorParameter = favorParameter;
   }

   public void setParameterName(String parameterName) {
      Assert.notNull(parameterName, "parameterName is required");
      this.parameterName = parameterName;
   }

   public void setMediaTypes(Properties mediaTypes) {
      if (!CollectionUtils.isEmpty(mediaTypes)) {
         mediaTypes.forEach((key, value) -> this.addMediaType((String)key, MediaType.valueOf((String)value)));
      }
   }

   public void addMediaType(String key, MediaType mediaType) {
      this.mediaTypes.put(key.toLowerCase(Locale.ROOT), mediaType);
   }

   public void addMediaTypes(@Nullable Map<String, MediaType> mediaTypes) {
      if (mediaTypes != null) {
         mediaTypes.forEach(this::addMediaType);
      }
   }

   public void setUseRegisteredExtensionsOnly(boolean useRegisteredExtensionsOnly) {
      this.useRegisteredExtensionsOnly = useRegisteredExtensionsOnly;
   }

   public void setIgnoreAcceptHeader(boolean ignoreAcceptHeader) {
      this.ignoreAcceptHeader = ignoreAcceptHeader;
   }

   public void setDefaultContentType(MediaType contentType) {
      this.defaultNegotiationStrategy = new FixedContentNegotiationStrategy(contentType);
   }

   public void setDefaultContentTypes(List<MediaType> contentTypes) {
      this.defaultNegotiationStrategy = new FixedContentNegotiationStrategy(contentTypes);
   }

   public void setDefaultContentTypeStrategy(ContentNegotiationStrategy strategy) {
      this.defaultNegotiationStrategy = strategy;
   }

   public void afterPropertiesSet() {
      this.build();
   }

   public ContentNegotiationManager build() {
      List<ContentNegotiationStrategy> strategies = new ArrayList<>();
      if (this.strategies != null) {
         strategies.addAll(this.strategies);
      } else {
         if (this.favorParameter) {
            ParameterContentNegotiationStrategy strategy = new ParameterContentNegotiationStrategy(this.mediaTypes);
            strategy.setParameterName(this.parameterName);
            if (this.useRegisteredExtensionsOnly != null) {
               strategy.setUseRegisteredExtensionsOnly(this.useRegisteredExtensionsOnly);
            } else {
               strategy.setUseRegisteredExtensionsOnly(true);
            }

            strategies.add(strategy);
         }

         if (!this.ignoreAcceptHeader) {
            strategies.add(new HeaderContentNegotiationStrategy());
         }

         if (this.defaultNegotiationStrategy != null) {
            strategies.add(this.defaultNegotiationStrategy);
         }
      }

      this.contentNegotiationManager = new ContentNegotiationManager(strategies);
      if (!CollectionUtils.isEmpty(this.mediaTypes) && !this.favorParameter) {
         this.contentNegotiationManager.addFileExtensionResolvers(new MappingMediaTypeFileExtensionResolver(this.mediaTypes));
      }

      return this.contentNegotiationManager;
   }

   public @Nullable ContentNegotiationManager getObject() {
      return this.contentNegotiationManager;
   }

   public Class<?> getObjectType() {
      return ContentNegotiationManager.class;
   }
}
