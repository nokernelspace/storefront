package org.springframework.http.client;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public final class MultipartBodyBuilder {
   private final LinkedMultiValueMap<String, MultipartBodyBuilder.DefaultPartBuilder> parts = new LinkedMultiValueMap();

   public MultipartBodyBuilder.PartBuilder part(String name, Object part) {
      return this.part(name, part, null);
   }

   public MultipartBodyBuilder.PartBuilder part(String name, Object part, @Nullable MediaType contentType) {
      Assert.hasLength(name, "'name' must not be empty");
      Assert.notNull(part, "'part' must not be null");
      if (part instanceof Part partObject) {
         MultipartBodyBuilder.PartBuilder builder = this.asyncPart(name, partObject.content(), DataBuffer.class);
         if (!partObject.headers().isEmpty()) {
            builder.headers(headers -> {
               headers.putAll(partObject.headers());
               String filename = headers.getContentDisposition().getFilename();
               headers.setContentDispositionFormData(name, filename);
            });
         }

         if (contentType != null) {
            builder.contentType(contentType);
         }

         return builder;
      } else if (part instanceof MultipartBodyBuilder.PublisherEntity<?, ?> publisherEntity) {
         MultipartBodyBuilder.PublisherPartBuilder<?, ?> builder = new MultipartBodyBuilder.PublisherPartBuilder<>(name, publisherEntity);
         if (contentType != null) {
            builder.contentType(contentType);
         }

         this.parts.add(name, builder);
         return builder;
      } else {
         HttpHeaders partHeaders = null;
         Object partBody;
         if (part instanceof HttpEntity<?> httpEntity) {
            partBody = httpEntity.getBody();
            partHeaders = new HttpHeaders();
            partHeaders.putAll(httpEntity.getHeaders());
         } else {
            partBody = part;
         }

         if (partBody instanceof Publisher) {
            throw new IllegalArgumentException(
               "Use asyncPart(String, Publisher, Class) or asyncPart(String, Publisher, ParameterizedTypeReference) or MultipartBodyBuilder.PublisherEntity"
            );
         }

         MultipartBodyBuilder.DefaultPartBuilder builder = new MultipartBodyBuilder.DefaultPartBuilder(name, partHeaders, partBody);
         if (contentType != null) {
            builder.contentType(contentType);
         }

         this.parts.add(name, builder);
         return builder;
      }
   }

   public <T, P extends Publisher<T>> MultipartBodyBuilder.PartBuilder asyncPart(String name, P publisher, Class<T> elementClass) {
      Assert.hasLength(name, "'name' must not be empty");
      Assert.notNull(publisher, "'publisher' must not be null");
      Assert.notNull(elementClass, "'elementClass' must not be null");
      MultipartBodyBuilder.PublisherPartBuilder<T, P> builder = new MultipartBodyBuilder.PublisherPartBuilder<>(name, null, publisher, elementClass);
      this.parts.add(name, builder);
      return builder;
   }

   public <T, P extends Publisher<T>> MultipartBodyBuilder.PartBuilder asyncPart(String name, P publisher, ParameterizedTypeReference<T> typeReference) {
      Assert.hasLength(name, "'name' must not be empty");
      Assert.notNull(publisher, "'publisher' must not be null");
      Assert.notNull(typeReference, "'typeReference' must not be null");
      MultipartBodyBuilder.PublisherPartBuilder<T, P> builder = new MultipartBodyBuilder.PublisherPartBuilder<>(name, null, publisher, typeReference);
      this.parts.add(name, builder);
      return builder;
   }

   public MultiValueMap<String, HttpEntity<?>> build() {
      MultiValueMap<String, HttpEntity<?>> result = new LinkedMultiValueMap(this.parts.size());

      for (Entry<String, List<MultipartBodyBuilder.DefaultPartBuilder>> entry : this.parts.entrySet()) {
         for (MultipartBodyBuilder.DefaultPartBuilder builder : entry.getValue()) {
            HttpEntity<?> entity = builder.build();
            result.add(entry.getKey(), entity);
         }
      }

      return result;
   }

   private static class DefaultPartBuilder implements MultipartBodyBuilder.PartBuilder {
      private final String name;
      protected @Nullable HttpHeaders headers;
      protected final @Nullable Object body;

      public DefaultPartBuilder(String name, @Nullable HttpHeaders headers, @Nullable Object body) {
         this.name = name;
         this.headers = headers;
         this.body = body;
      }

      @Override
      public MultipartBodyBuilder.PartBuilder contentType(MediaType contentType) {
         this.initHeadersIfNecessary().setContentType(contentType);
         return this;
      }

      @Override
      public MultipartBodyBuilder.PartBuilder filename(String filename) {
         this.initHeadersIfNecessary().setContentDispositionFormData(this.name, filename);
         return this;
      }

      @Override
      public MultipartBodyBuilder.PartBuilder header(String headerName, String... headerValues) {
         this.initHeadersIfNecessary().addAll(headerName, Arrays.asList(headerValues));
         return this;
      }

      @Override
      public MultipartBodyBuilder.PartBuilder headers(Consumer<HttpHeaders> headersConsumer) {
         headersConsumer.accept(this.initHeadersIfNecessary());
         return this;
      }

      private HttpHeaders initHeadersIfNecessary() {
         if (this.headers == null) {
            this.headers = new HttpHeaders();
         }

         return this.headers;
      }

      public HttpEntity<?> build() {
         return new HttpEntity<>(this.body, this.headers);
      }
   }

   public interface PartBuilder {
      MultipartBodyBuilder.PartBuilder contentType(MediaType contentType);

      MultipartBodyBuilder.PartBuilder filename(String filename);

      MultipartBodyBuilder.PartBuilder header(String headerName, String... headerValues);

      MultipartBodyBuilder.PartBuilder headers(Consumer<HttpHeaders> headersConsumer);
   }

   static final class PublisherEntity<T, P extends Publisher<T>> extends HttpEntity<P> implements ResolvableTypeProvider {
      private final ResolvableType resolvableType;

      PublisherEntity(@Nullable HttpHeaders headers, P publisher, ResolvableType resolvableType) {
         super(publisher, headers);
         Assert.notNull(publisher, "'publisher' must not be null");
         Assert.notNull(resolvableType, "'resolvableType' must not be null");
         this.resolvableType = resolvableType;
      }

      public ResolvableType getResolvableType() {
         return this.resolvableType;
      }
   }

   private static class PublisherPartBuilder<S, P extends Publisher<S>> extends MultipartBodyBuilder.DefaultPartBuilder {
      private final ResolvableType resolvableType;

      public PublisherPartBuilder(String name, @Nullable HttpHeaders headers, P body, Class<S> elementClass) {
         super(name, headers, body);
         this.resolvableType = ResolvableType.forClass(elementClass);
      }

      public PublisherPartBuilder(String name, @Nullable HttpHeaders headers, P body, ParameterizedTypeReference<S> typeRef) {
         super(name, headers, body);
         this.resolvableType = ResolvableType.forType(typeRef);
      }

      public PublisherPartBuilder(String name, MultipartBodyBuilder.PublisherEntity<S, P> other) {
         super(name, other.getHeaders(), other.getBody());
         this.resolvableType = other.getResolvableType();
      }

      @Override
      public HttpEntity<?> build() {
         P publisher = (P)this.body;
         Assert.state(publisher != null, "Publisher must not be null");
         return new MultipartBodyBuilder.PublisherEntity(this.headers, publisher, this.resolvableType);
      }
   }
}
