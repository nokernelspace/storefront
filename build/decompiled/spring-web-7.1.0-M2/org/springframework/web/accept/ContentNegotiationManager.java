package org.springframework.web.accept;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.NativeWebRequest;

public class ContentNegotiationManager implements ContentNegotiationStrategy, MediaTypeFileExtensionResolver {
   private final List<ContentNegotiationStrategy> strategies = new ArrayList<>();
   private final Set<MediaTypeFileExtensionResolver> resolvers = new LinkedHashSet<>();

   public ContentNegotiationManager(ContentNegotiationStrategy... strategies) {
      this(Arrays.asList(strategies));
   }

   public ContentNegotiationManager(Collection<ContentNegotiationStrategy> strategies) {
      Assert.notEmpty(strategies, "At least one ContentNegotiationStrategy is expected");
      this.strategies.addAll(strategies);

      for (ContentNegotiationStrategy strategy : this.strategies) {
         if (strategy instanceof MediaTypeFileExtensionResolver mediaTypeFileExtensionResolver) {
            this.resolvers.add(mediaTypeFileExtensionResolver);
         }
      }
   }

   public ContentNegotiationManager() {
      this(new HeaderContentNegotiationStrategy());
   }

   public List<ContentNegotiationStrategy> getStrategies() {
      return this.strategies;
   }

   public <T extends ContentNegotiationStrategy> @Nullable T getStrategy(Class<T> strategyType) {
      for (ContentNegotiationStrategy strategy : this.getStrategies()) {
         if (strategyType.isInstance(strategy)) {
            return (T)strategy;
         }
      }

      return null;
   }

   public void addFileExtensionResolvers(MediaTypeFileExtensionResolver... resolvers) {
      Collections.addAll(this.resolvers, resolvers);
   }

   @Override
   public List<MediaType> resolveMediaTypes(NativeWebRequest request) throws HttpMediaTypeNotAcceptableException {
      for (ContentNegotiationStrategy strategy : this.strategies) {
         List<MediaType> mediaTypes = strategy.resolveMediaTypes(request);
         if (!mediaTypes.equals(MEDIA_TYPE_ALL_LIST)) {
            return mediaTypes;
         }
      }

      return MEDIA_TYPE_ALL_LIST;
   }

   @Override
   public List<String> resolveFileExtensions(MediaType mediaType) {
      return this.doResolveExtensions(resolver -> resolver.resolveFileExtensions(mediaType));
   }

   @Override
   public List<String> getAllFileExtensions() {
      return this.doResolveExtensions(MediaTypeFileExtensionResolver::getAllFileExtensions);
   }

   private List<String> doResolveExtensions(Function<MediaTypeFileExtensionResolver, List<String>> extractor) {
      List<String> result = null;

      for (MediaTypeFileExtensionResolver resolver : this.resolvers) {
         List<String> extensions = extractor.apply(resolver);
         if (!CollectionUtils.isEmpty(extensions)) {
            result = result != null ? result : new ArrayList<>(4);

            for (String extension : extensions) {
               if (!result.contains(extension)) {
                  result.add(extension);
               }
            }
         }
      }

      return result != null ? result : Collections.emptyList();
   }

   public Map<String, MediaType> getMediaTypeMappings() {
      Map<String, MediaType> result = null;

      for (MediaTypeFileExtensionResolver resolver : this.resolvers) {
         if (resolver instanceof MappingMediaTypeFileExtensionResolver mappingResolver) {
            Map<String, MediaType> map = mappingResolver.getMediaTypes();
            if (!CollectionUtils.isEmpty(map)) {
               result = result != null ? result : new HashMap<>(4);
               result.putAll(map);
            }
         }
      }

      return result != null ? result : Collections.emptyMap();
   }
}
