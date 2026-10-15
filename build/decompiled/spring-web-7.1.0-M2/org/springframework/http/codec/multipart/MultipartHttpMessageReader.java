package org.springframework.http.codec.multipart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.LoggingCodecSupport;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MultipartHttpMessageReader extends LoggingCodecSupport implements HttpMessageReader<MultiValueMap<String, Part>> {
   private static final ResolvableType MULTIPART_VALUE_TYPE = ResolvableType.forClassWithGenerics(MultiValueMap.class, new Class[]{String.class, Part.class});
   static final List<MediaType> MIME_TYPES = List.of(MediaType.MULTIPART_FORM_DATA, MediaType.MULTIPART_MIXED, MediaType.MULTIPART_RELATED);
   private final HttpMessageReader<Part> partReader;

   public MultipartHttpMessageReader(HttpMessageReader<Part> partReader) {
      Assert.notNull(partReader, "'partReader' is required");
      this.partReader = partReader;
   }

   public HttpMessageReader<Part> getPartReader() {
      return this.partReader;
   }

   @Override
   public List<MediaType> getReadableMediaTypes() {
      return MIME_TYPES;
   }

   @Override
   public boolean canRead(ResolvableType elementType, @Nullable MediaType mediaType) {
      return this.supportsMediaType(mediaType) && MULTIPART_VALUE_TYPE.isAssignableFrom(elementType);
   }

   private boolean supportsMediaType(@Nullable MediaType mediaType) {
      if (mediaType == null) {
         return true;
      }

      for (MediaType supportedMediaType : MIME_TYPES) {
         if (supportedMediaType.isCompatibleWith(mediaType)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public Flux<MultiValueMap<String, Part>> read(ResolvableType elementType, ReactiveHttpInputMessage message, Map<String, Object> hints) {
      return Flux.from(this.readMono(elementType, message, hints));
   }

   @Override
   public Mono<MultiValueMap<String, Part>> readMono(ResolvableType elementType, ReactiveHttpInputMessage inputMessage, Map<String, Object> hints) {
      Map<String, Object> allHints = Hints.merge(hints, Hints.SUPPRESS_LOGGING_HINT, true);
      return this.partReader
         .read(elementType, inputMessage, allHints)
         .collectMultimap(Part::name)
         .doOnDiscard(Map.class, map -> map.values().forEach(parts -> parts.forEach(part -> part.delete().subscribe())))
         .doOnNext(
            map -> LogFormatUtils.traceDebug(
               this.logger,
               traceOn -> Hints.getLogPrefix(hints)
                  + "Parsed "
                  + (this.isEnableLoggingRequestDetails() ? LogFormatUtils.formatValue(map, !traceOn) : "parts " + map.keySet() + " (content masked)")
            )
         )
         .map(this::toMultiValueMap);
   }

   private LinkedMultiValueMap<String, Part> toMultiValueMap(Map<String, Collection<Part>> map) {
      return new LinkedMultiValueMap(map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> this.toList((Collection<Part>)e.getValue()))));
   }

   private List<Part> toList(Collection<Part> collection) {
      return collection instanceof List<Part> list ? list : new ArrayList<>(collection);
   }
}
