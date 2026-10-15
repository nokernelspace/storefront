package org.springframework.boot.info;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;

@ImportRuntimeHints(GitProperties.GitPropertiesRuntimeHints.class)
public class GitProperties extends InfoProperties {
   static final Set<GitProperties.Coercer> coercers = Set.of(
      GitProperties.Coercer.milliseconds(),
      GitProperties.Coercer.dateTimePattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
      GitProperties.Coercer.dateTimePattern("yyyy-MM-dd'T'HH:mm:ssZ")
   );

   public GitProperties(Properties entries) {
      super(processEntries(entries));
   }

   public @Nullable String getBranch() {
      return this.get("branch");
   }

   public @Nullable String getCommitId() {
      return this.get("commit.id");
   }

   public @Nullable String getShortCommitId() {
      String shortId = this.get("commit.id.abbrev");
      if (shortId != null) {
         return shortId;
      } else {
         String id = this.getCommitId();
         if (id == null) {
            return null;
         } else {
            return id.length() > 7 ? id.substring(0, 7) : id;
         }
      }
   }

   public @Nullable Instant getCommitTime() {
      return this.getInstant("commit.time");
   }

   private static Properties processEntries(Properties properties) {
      coercePropertyToEpoch(properties, "commit.time");
      coercePropertyToEpoch(properties, "build.time");
      Object commitId = properties.get("commit.id");
      if (commitId != null) {
         properties.put("commit.id.full", commitId);
      }

      return properties;
   }

   private static void coercePropertyToEpoch(Properties properties, String key) {
      String value = properties.getProperty(key);
      if (value != null) {
         properties.setProperty(key, coercers.stream().map(coercer -> coercer.apply(value)).filter(Objects::nonNull).findFirst().orElse(value));
      }
   }

   private record Coercer(Function<String, @Nullable Long> action, Predicate<RuntimeException> ignoredExceptions) {
      @Nullable String apply(String value) {
         try {
            Long result = this.action.apply(value);
            return result != null ? String.valueOf(result) : null;
         } catch (RuntimeException ex) {
            if (this.ignoredExceptions.test(ex)) {
               return null;
            } else {
               throw ex;
            }
         }
      }

      static GitProperties.Coercer milliseconds() {
         return new GitProperties.Coercer(value -> Long.parseLong(value) * 1000L, NumberFormatException.class::isInstance);
      }

      static GitProperties.Coercer dateTimePattern(String pattern) {
         return new GitProperties.Coercer(
            value -> DateTimeFormatter.ofPattern(pattern).parse(value, Instant::from).toEpochMilli(), DateTimeParseException.class::isInstance
         );
      }
   }

   static class GitPropertiesRuntimeHints implements RuntimeHintsRegistrar {
      public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
         hints.resources().registerPattern("git.properties");
      }
   }
}
