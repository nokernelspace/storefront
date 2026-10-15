package org.springframework.boot.context.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.SystemPropertyUtils;

class ConfigDataEnvironmentContributorPlaceholdersResolver implements PlaceholdersResolver {
   private final Iterable<ConfigDataEnvironmentContributor> contributors;
   private final @Nullable ConfigDataActivationContext activationContext;
   private final boolean failOnResolveFromInactiveContributor;
   private final PropertyPlaceholderHelper helper;
   private final @Nullable ConfigDataEnvironmentContributor activeContributor;
   private final ConversionService conversionService;

   ConfigDataEnvironmentContributorPlaceholdersResolver(
      Iterable<ConfigDataEnvironmentContributor> contributors,
      @Nullable ConfigDataActivationContext activationContext,
      @Nullable ConfigDataEnvironmentContributor activeContributor,
      boolean failOnResolveFromInactiveContributor,
      ConversionService conversionService
   ) {
      this.contributors = contributors;
      this.activationContext = activationContext;
      this.activeContributor = activeContributor;
      this.failOnResolveFromInactiveContributor = failOnResolveFromInactiveContributor;
      this.conversionService = conversionService;
      this.helper = new PropertyPlaceholderHelper("${", "}", ":", SystemPropertyUtils.ESCAPE_CHARACTER, true);
   }

   @Override
   public @Nullable Object resolvePlaceholders(@Nullable Object value) {
      return value instanceof String string ? this.helper.replacePlaceholders(string, this::resolvePlaceholder) : value;
   }

   private @Nullable String resolvePlaceholder(String placeholder) {
      Object result = null;

      for (ConfigDataEnvironmentContributor contributor : this.contributors) {
         PropertySource<?> propertySource = contributor.getPropertySource();
         Object value = propertySource != null ? propertySource.getProperty(placeholder) : null;
         if (value != null && !this.isActive(contributor)) {
            if (this.failOnResolveFromInactiveContributor) {
               ConfigDataResource resource = contributor.getResource();
               Assert.state(propertySource != null, "'propertySource' can't be null here");
               Origin origin = OriginLookup.getOrigin(propertySource, placeholder);
               throw new InactiveConfigDataAccessException(propertySource, resource, placeholder, origin);
            }

            value = null;
         }

         result = result != null ? result : value;
      }

      return result != null ? this.convertValueIfNecessary(result) : null;
   }

   private boolean isActive(ConfigDataEnvironmentContributor contributor) {
      if (contributor == this.activeContributor) {
         return true;
      } else {
         return contributor.getKind() != ConfigDataEnvironmentContributor.Kind.UNBOUND_IMPORT
            ? contributor.isActive(this.activationContext)
            : contributor.withBoundProperties(this.contributors, this.activationContext).isActive(this.activationContext);
      }
   }

   private @Nullable String convertValueIfNecessary(Object value) {
      return value instanceof String string ? string : (String)this.conversionService.convert(value, String.class);
   }
}
