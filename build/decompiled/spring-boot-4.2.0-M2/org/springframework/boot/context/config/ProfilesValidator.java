package org.springframework.boot.context.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.BindContext;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

final class ProfilesValidator implements BindHandler {
   private static final String ALLOWED_CHARS = "-_.+@";
   private final boolean validate;

   private ProfilesValidator(boolean validate) {
      this.validate = validate;
   }

   @Override
   public Object onSuccess(ConfigurationPropertyName name, Bindable<?> target, BindContext context, Object result) {
      this.validate(result);
      return result;
   }

   void validate(Object value, Supplier<String> wrappedExceptionMessage) {
      try {
         this.validate(value);
      } catch (IllegalStateException ex) {
         throw new IllegalStateException(wrappedExceptionMessage.get(), ex);
      }
   }

   private void validate(@Nullable Object value) {
      if (this.validate) {
         if (value instanceof Collection<?> list) {
            list.forEach(this::validate);
         } else if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> this.validate(v));
         } else {
            String profile = value != null ? value.toString() : null;
            Assert.state(StringUtils.hasText(profile), "Invalid empty profile");

            for (int i = 0; i < profile.length(); i++) {
               int codePoint = profile.codePointAt(i);
               boolean isAllowedChar = "-_.+@".indexOf(codePoint) != -1;
               Assert.state(
                  isAllowedChar || Character.isLetterOrDigit(codePoint),
                  () -> "Profile '%s' must contain a letter, digit or allowed char (%s)"
                     .formatted(profile, Arrays.stream("-_.+@".split("")).collect(Collectors.joining("', '", "'", "'")))
               );
               Assert.state(
                  i > 0 && i < profile.length() - 1 || Character.isLetterOrDigit(codePoint),
                  () -> "Profile '%s' must start and end with a letter or digit".formatted(profile)
               );
            }
         }
      }
   }

   static ProfilesValidator get(Binder binder) {
      return new ProfilesValidator(binder.bind("spring.profiles.validate", Boolean.class).orElse(true));
   }
}
