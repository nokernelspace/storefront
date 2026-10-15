package org.springframework.beans.factory.config;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.CollectionFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.inspector.TagInspector;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.reader.UnicodeReader;
import org.yaml.snakeyaml.representer.Representer;

public abstract class YamlProcessor {
   private final Log logger = LogFactory.getLog(this.getClass());
   private YamlProcessor.ResolutionMethod resolutionMethod = YamlProcessor.ResolutionMethod.OVERRIDE;
   private Resource[] resources = new Resource[0];
   private List<YamlProcessor.DocumentMatcher> documentMatchers = Collections.emptyList();
   private boolean matchDefault = true;
   private Set<String> supportedTypes = Collections.emptySet();

   public void setDocumentMatchers(YamlProcessor.DocumentMatcher... matchers) {
      this.documentMatchers = List.of(matchers);
   }

   public void setMatchDefault(boolean matchDefault) {
      this.matchDefault = matchDefault;
   }

   public void setResolutionMethod(YamlProcessor.ResolutionMethod resolutionMethod) {
      Assert.notNull(resolutionMethod, "ResolutionMethod must not be null");
      this.resolutionMethod = resolutionMethod;
   }

   public void setResources(Resource... resources) {
      this.resources = resources;
   }

   public void setSupportedTypes(Class<?>... supportedTypes) {
      if (ObjectUtils.isEmpty(supportedTypes)) {
         this.supportedTypes = Collections.emptySet();
      } else {
         Assert.noNullElements(supportedTypes, "'supportedTypes' must not contain null elements");
         this.supportedTypes = Arrays.stream(supportedTypes).map(Class::getName).collect(Collectors.toUnmodifiableSet());
      }
   }

   protected void process(YamlProcessor.MatchCallback callback) {
      Yaml yaml = this.createYaml();

      for (Resource resource : this.resources) {
         boolean found = this.process(callback, yaml, resource);
         if (this.resolutionMethod == YamlProcessor.ResolutionMethod.FIRST_FOUND && found) {
            return;
         }
      }
   }

   protected Yaml createYaml() {
      LoaderOptions loaderOptions = new LoaderOptions();
      loaderOptions.setAllowDuplicateKeys(false);
      loaderOptions.setTagInspector(new YamlProcessor.SupportedTagInspector());
      DumperOptions dumperOptions = new DumperOptions();
      return new Yaml(new Constructor(loaderOptions), new Representer(dumperOptions), dumperOptions, loaderOptions);
   }

   private boolean process(YamlProcessor.MatchCallback callback, Yaml yaml, Resource resource) {
      AtomicInteger count = new AtomicInteger();

      try {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Loading from YAML: " + resource);
         }

         resource.consumeContent(inputStream -> {
            Reader reader = new UnicodeReader(inputStream);

            for (Object object : yaml.loadAll(reader)) {
               if (object != null && this.process(this.asMap(object), callback)) {
                  count.incrementAndGet();
                  if (this.resolutionMethod == YamlProcessor.ResolutionMethod.FIRST_FOUND) {
                     break;
                  }
               }
            }

            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Loaded " + count + " document" + (count.get() > 1 ? "s" : "") + " from YAML resource: " + resource);
            }
         });
      } catch (IOException ex) {
         this.handleProcessError(resource, ex);
      }

      return count.get() > 0;
   }

   private void handleProcessError(Resource resource, IOException ex) {
      if (this.resolutionMethod != YamlProcessor.ResolutionMethod.FIRST_FOUND && this.resolutionMethod != YamlProcessor.ResolutionMethod.OVERRIDE_AND_IGNORE) {
         throw new IllegalStateException(ex);
      }

      if (this.logger.isWarnEnabled()) {
         this.logger.warn("Could not load map from " + resource + ": " + ex.getMessage());
      }
   }

   private Map<String, Object> asMap(Object object) {
      Map<String, Object> result = new LinkedHashMap<>();
      if (object instanceof Map map) {
         map.forEach((key, value) -> {
            if (value instanceof Map) {
               value = this.asMap(value);
            }

            if (key instanceof CharSequence) {
               result.put(key.toString(), value);
            } else {
               result.put("[" + key + "]", value);
            }
         });
         return result;
      } else {
         result.put("document", object);
         return result;
      }
   }

   private boolean process(Map<String, Object> map, YamlProcessor.MatchCallback callback) {
      Properties properties = CollectionFactory.createStringAdaptingProperties();
      properties.putAll(this.getFlattenedMap(map));
      if (this.documentMatchers.isEmpty()) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Merging document (no matchers set): " + map);
         }

         callback.process(properties, map);
         return true;
      } else {
         YamlProcessor.MatchStatus result = YamlProcessor.MatchStatus.ABSTAIN;

         for (YamlProcessor.DocumentMatcher matcher : this.documentMatchers) {
            YamlProcessor.MatchStatus match = matcher.matches(properties);
            result = YamlProcessor.MatchStatus.getMostSpecific(match, result);
            if (match == YamlProcessor.MatchStatus.FOUND) {
               if (this.logger.isDebugEnabled()) {
                  this.logger.debug("Matched document with document matcher: " + properties);
               }

               callback.process(properties, map);
               return true;
            }
         }

         if (result == YamlProcessor.MatchStatus.ABSTAIN && this.matchDefault) {
            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Matched document with default matcher: " + map);
            }

            callback.process(properties, map);
            return true;
         } else {
            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Unmatched document: " + map);
            }

            return false;
         }
      }
   }

   protected final Map<String, Object> getFlattenedMap(Map<String, Object> source) {
      return this.getFlattenedMap(source, false, null);
   }

   protected final Map<String, Object> getFlattenedMap(Map<String, Object> source, boolean includeEmpty, @Nullable Object emptyValue) {
      Map<String, Object> result = new LinkedHashMap<>();
      this.buildFlattenedMap(result, source, null, includeEmpty, emptyValue);
      return result;
   }

   private void buildFlattenedMap(
      Map<String, Object> result, Map<String, Object> source, @Nullable String path, boolean includeEmpty, @Nullable Object emptyValue
   ) {
      if (includeEmpty && source.isEmpty()) {
         result.put(path, emptyValue);
      } else {
         source.forEach((key, value) -> {
            if (StringUtils.hasText(path)) {
               if (key.startsWith("[")) {
                  key = path + key;
               } else {
                  key = path + "." + key;
               }
            }

            if (value instanceof String) {
               result.put(key, value);
            } else if (value instanceof Map map) {
               this.buildFlattenedMap(result, map, key, includeEmpty, emptyValue);
            } else if (value instanceof Collection collection) {
               if (collection.isEmpty()) {
                  result.put(key, "");
               } else {
                  int count = 0;

                  for (Object object : collection) {
                     this.buildFlattenedMap(result, Collections.singletonMap("[" + count++ + "]", object), key, includeEmpty, emptyValue);
                  }
               }
            } else {
               result.put(key, value != null ? value : (includeEmpty ? emptyValue : ""));
            }
         });
      }
   }

   @FunctionalInterface
   public interface DocumentMatcher {
      YamlProcessor.MatchStatus matches(Properties properties);
   }

   @FunctionalInterface
   public interface MatchCallback {
      void process(Properties properties, Map<String, Object> map);
   }

   public enum MatchStatus {
      FOUND,
      NOT_FOUND,
      ABSTAIN;

      public static YamlProcessor.MatchStatus getMostSpecific(YamlProcessor.MatchStatus a, YamlProcessor.MatchStatus b) {
         return a.ordinal() < b.ordinal() ? a : b;
      }
   }

   public enum ResolutionMethod {
      OVERRIDE,
      OVERRIDE_AND_IGNORE,
      FIRST_FOUND;
   }

   private class SupportedTagInspector implements TagInspector {
      public boolean isGlobalTagAllowed(Tag tag) {
         return YamlProcessor.this.supportedTypes.contains(tag.getClassName());
      }
   }
}
