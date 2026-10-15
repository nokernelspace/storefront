package org.springframework.web.accept;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;

public class MappingMediaTypeFileExtensionResolver implements MediaTypeFileExtensionResolver {
   private final ConcurrentMap<String, MediaType> mediaTypes = new ConcurrentHashMap<>(64);
   private final ConcurrentMap<MediaType, List<String>> fileExtensions = new ConcurrentHashMap<>(64);
   private final List<String> allFileExtensions = new CopyOnWriteArrayList<>();

   public MappingMediaTypeFileExtensionResolver(@Nullable Map<String, MediaType> mediaTypes) {
      if (mediaTypes != null) {
         Set<String> allFileExtensions = CollectionUtils.newHashSet(mediaTypes.size());
         mediaTypes.forEach(this::addMapping);
         this.allFileExtensions.addAll(allFileExtensions);
      }
   }

   public Map<String, MediaType> getMediaTypes() {
      return this.mediaTypes;
   }

   protected List<MediaType> getAllMediaTypes() {
      return new ArrayList<>(this.mediaTypes.values());
   }

   protected void addMapping(String key, MediaType mediaType) {
      key = key.toLowerCase(Locale.ROOT);
      MediaType previous = this.mediaTypes.putIfAbsent(key, mediaType);
      if (previous == null) {
         this.fileExtensions.computeIfAbsent(mediaType, k -> new CopyOnWriteArrayList<>()).add(key);
         this.allFileExtensions.add(key);
      }
   }

   @Override
   public List<String> resolveFileExtensions(MediaType mediaType) {
      List<String> fileExtensions = this.fileExtensions.get(mediaType.removeQualityValue());
      return fileExtensions != null ? fileExtensions : Collections.emptyList();
   }

   @Override
   public List<String> getAllFileExtensions() {
      return Collections.unmodifiableList(this.allFileExtensions);
   }

   protected @Nullable MediaType lookupMediaType(String extension) {
      return this.mediaTypes.get(extension.toLowerCase(Locale.ROOT));
   }
}
