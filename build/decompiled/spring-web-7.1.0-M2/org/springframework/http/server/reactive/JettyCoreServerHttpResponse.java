package org.springframework.http.server.reactive;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Map;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpCookie.SameSite;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.io.Content.Source;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.HttpCookieUtils.SetCookieHttpField;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty.util.Callback.Completable;
import org.eclipse.jetty.util.IteratingCallback.Action;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.JettyDataBufferFactory;
import org.springframework.core.io.buffer.DataBuffer.ByteBufferIterator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.support.JettyHeadersAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class JettyCoreServerHttpResponse extends AbstractServerHttpResponse implements ZeroCopyHttpOutputMessage {
   private final Response response;

   public JettyCoreServerHttpResponse(Response response, JettyDataBufferFactory dataBufferFactory) {
      super(dataBufferFactory, new HttpHeaders(new JettyHeadersAdapter(response.getHeaders())));
      this.response = response;
      ListIterator<HttpField> it = this.response.getHeaders().listIterator();

      while (it.hasNext()) {
         HttpField field = it.next();
         if (field instanceof SetCookieHttpField setCookieHttpField) {
            HttpCookie httpCookie = setCookieHttpField.getHttpCookie();
            ResponseCookie responseCookie = ResponseCookie.from(httpCookie.getName(), httpCookie.getValue())
               .httpOnly(httpCookie.isHttpOnly())
               .domain(httpCookie.getDomain())
               .maxAge(httpCookie.getMaxAge())
               .sameSite(httpCookie.getSameSite().name())
               .secure(httpCookie.isSecure())
               .partitioned(httpCookie.isPartitioned())
               .build();
            this.addCookie(responseCookie);
            it.remove();
         }
      }
   }

   @Override
   protected Mono<Void> writeWithInternal(Publisher<? extends DataBuffer> body) {
      return Flux.from(body).concatMap(this::sendDataBuffer).then();
   }

   @Override
   protected Mono<Void> writeAndFlushWithInternal(Publisher<? extends Publisher<? extends DataBuffer>> body) {
      return Flux.from(body).concatMap(this::writeWithInternal).then();
   }

   @Override
   protected void applyStatusCode() {
      HttpStatusCode status = this.getStatusCode();
      if (status != null) {
         this.response.setStatus(status.value());
      }
   }

   @Override
   protected void applyHeaders() {
   }

   @Override
   protected void applyCookies() {
      this.getCookies()
         .values()
         .stream()
         .flatMap(Collection::stream)
         .forEach(cookie -> Response.addCookie(this.response, new JettyCoreServerHttpResponse.ResponseHttpCookie(cookie)));
   }

   @Override
   public Mono<Void> writeWith(Path file, long position, long count) {
      Completable callback = new Completable();
      Mono<Void> mono = Mono.fromFuture(callback);

      try {
         Content.copy(Source.from(null, file, position, count), this.response, callback);
      } catch (Throwable th) {
         callback.failed(th);
      }

      return this.doCommit(() -> mono);
   }

   private Mono<Void> sendDataBuffer(DataBuffer dataBuffer) {
      return Mono.defer(() -> {
         final ByteBufferIterator byteBufferIterator = dataBuffer.readableByteBuffers();
         final Completable callback = new Completable();
         (new IteratingCallback() {
            protected Action process() {
               if (!byteBufferIterator.hasNext()) {
                  return Action.SUCCEEDED;
               }

               JettyCoreServerHttpResponse.this.response.write(false, (ByteBuffer)byteBufferIterator.next(), this);
               return Action.SCHEDULED;
            }

            protected void onCompleteSuccess() {
               byteBufferIterator.close();
               DataBufferUtils.release(dataBuffer);
               callback.complete(null);
            }

            protected void onCompleteFailure(Throwable cause) {
               byteBufferIterator.close();
               DataBufferUtils.release(dataBuffer);
               callback.failed(cause);
            }
         }).iterate();
         return Mono.fromFuture(callback);
      });
   }

   @Override
   public <T> T getNativeResponse() {
      return (T)this.response;
   }

   private static class ResponseHttpCookie implements HttpCookie {
      private final ResponseCookie responseCookie;

      ResponseHttpCookie(ResponseCookie responseCookie) {
         this.responseCookie = responseCookie;
      }

      public String getName() {
         return this.responseCookie.getName();
      }

      public String getValue() {
         return this.responseCookie.getValue();
      }

      public int getVersion() {
         return 0;
      }

      public long getMaxAge() {
         return this.responseCookie.getMaxAge().toSeconds();
      }

      public @Nullable String getComment() {
         return null;
      }

      public @Nullable String getDomain() {
         return this.responseCookie.getDomain();
      }

      public @Nullable String getPath() {
         return this.responseCookie.getPath();
      }

      public boolean isSecure() {
         return this.responseCookie.isSecure();
      }

      public SameSite getSameSite() {
         return SameSite.from(this.responseCookie.getSameSite());
      }

      public boolean isHttpOnly() {
         return this.responseCookie.isHttpOnly();
      }

      public boolean isPartitioned() {
         return this.responseCookie.isPartitioned();
      }

      public Map<String, String> getAttributes() {
         return Collections.emptyMap();
      }
   }
}
