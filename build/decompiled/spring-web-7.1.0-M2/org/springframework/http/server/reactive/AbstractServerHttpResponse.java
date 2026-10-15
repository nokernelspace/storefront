package org.springframework.http.server.reactive;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractServerHttpResponse implements ServerHttpResponse {
   private final DataBufferFactory dataBufferFactory;
   private @Nullable HttpStatusCode statusCode;
   private final HttpHeaders headers;
   private final MultiValueMap<String, ResponseCookie> cookies;
   private final AtomicReference<AbstractServerHttpResponse.State> state = new AtomicReference<>(AbstractServerHttpResponse.State.NEW);
   private final List<Supplier<? extends Mono<Void>>> commitActions = new CopyOnWriteArrayList<>();
   private @Nullable HttpHeaders readOnlyHeaders;

   public AbstractServerHttpResponse(DataBufferFactory dataBufferFactory) {
      this(dataBufferFactory, new HttpHeaders());
   }

   public AbstractServerHttpResponse(DataBufferFactory dataBufferFactory, HttpHeaders headers) {
      Assert.notNull(dataBufferFactory, "DataBufferFactory must not be null");
      Assert.notNull(headers, "HttpHeaders must not be null");
      this.dataBufferFactory = dataBufferFactory;
      this.headers = headers;
      this.cookies = new LinkedMultiValueMap();
   }

   @Override
   public final DataBufferFactory bufferFactory() {
      return this.dataBufferFactory;
   }

   @Override
   public boolean setStatusCode(@Nullable HttpStatusCode status) {
      if (this.state.get() == AbstractServerHttpResponse.State.COMMITTED) {
         return false;
      }

      this.statusCode = status;
      return true;
   }

   @Override
   public @Nullable HttpStatusCode getStatusCode() {
      return this.statusCode;
   }

   @Override
   public boolean setRawStatusCode(@Nullable Integer statusCode) {
      return this.setStatusCode(statusCode != null ? HttpStatusCode.valueOf(statusCode) : null);
   }

   @Override
   public HttpHeaders getHeaders() {
      if (this.readOnlyHeaders != null) {
         return this.readOnlyHeaders;
      } else if (this.state.get() == AbstractServerHttpResponse.State.COMMITTED) {
         this.readOnlyHeaders = HttpHeaders.readOnlyHttpHeaders(this.headers);
         return this.readOnlyHeaders;
      } else {
         return this.headers;
      }
   }

   @Override
   public MultiValueMap<String, ResponseCookie> getCookies() {
      return this.state.get() == AbstractServerHttpResponse.State.COMMITTED ? CollectionUtils.unmodifiableMultiValueMap(this.cookies) : this.cookies;
   }

   @Override
   public void addCookie(ResponseCookie cookie) {
      Assert.notNull(cookie, "ResponseCookie must not be null");
      if (this.state.get() == AbstractServerHttpResponse.State.COMMITTED) {
         throw new IllegalStateException("Can't add the cookie " + cookie + "because the HTTP response has already been committed");
      }

      this.getCookies().add(cookie.getName(), cookie);
   }

   public abstract <T> T getNativeResponse();

   @Override
   public void beforeCommit(Supplier<? extends Mono<Void>> action) {
      this.commitActions.add(action);
   }

   @Override
   public boolean isCommitted() {
      AbstractServerHttpResponse.State state = this.state.get();
      return state != AbstractServerHttpResponse.State.NEW && state != AbstractServerHttpResponse.State.COMMIT_ACTION_FAILED;
   }

   @Override
   public final Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
      return body instanceof Mono
         ? ((Mono)body)
            .flatMap(
               buffer -> {
                  this.touchDataBuffer(buffer);
                  AtomicBoolean subscribed = new AtomicBoolean();
                  return this.doCommit(
                        () -> {
                           try {
                              return this.writeWithInternal(
                                 Mono.fromCallable(() -> buffer)
                                    .doOnSubscribe(s -> subscribed.set(true))
                                    .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                              );
                           } catch (Throwable ex) {
                              return Mono.error(ex);
                           }
                        }
                     )
                     .doOnError(ex -> DataBufferUtils.release(buffer))
                     .doOnCancel(() -> {
                        if (!subscribed.get()) {
                           DataBufferUtils.release(buffer);
                        }
                     });
               }
            )
            .doOnError(t -> this.getHeaders().clearContentHeaders())
            .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
         : new ChannelSendOperator(body, inner -> this.doCommit(() -> this.writeWithInternal(inner))).doOnError(t -> this.getHeaders().clearContentHeaders());
   }

   @Override
   public final Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
      return new ChannelSendOperator(body, inner -> this.doCommit(() -> this.writeAndFlushWithInternal(inner)))
         .doOnError(t -> this.getHeaders().clearContentHeaders());
   }

   @Override
   public Mono<Void> setComplete() {
      return !this.isCommitted() ? this.doCommit(null) : Mono.empty();
   }

   protected Mono<Void> doCommit() {
      return this.doCommit(null);
   }

   protected Mono<Void> doCommit(@Nullable Supplier<? extends Mono<Void>> writeAction) {
      Flux<Void> allActions = Flux.empty();
      if (this.state.compareAndSet(AbstractServerHttpResponse.State.NEW, AbstractServerHttpResponse.State.COMMITTING)) {
         if (!this.commitActions.isEmpty()) {
            allActions = Flux.concat(Flux.fromIterable(this.commitActions).map(Supplier::get)).doOnError(ex -> {
               if (this.state.compareAndSet(AbstractServerHttpResponse.State.COMMITTING, AbstractServerHttpResponse.State.COMMIT_ACTION_FAILED)) {
                  this.getHeaders().clearContentHeaders();
               }
            });
         }
      } else if (!this.state.compareAndSet(AbstractServerHttpResponse.State.COMMIT_ACTION_FAILED, AbstractServerHttpResponse.State.COMMITTING)) {
         return Mono.empty();
      }

      allActions = allActions.concatWith(Mono.fromRunnable(() -> {
         this.applyStatusCode();
         this.applyHeaders();
         this.applyCookies();
         this.state.set(AbstractServerHttpResponse.State.COMMITTED);
      }));
      if (writeAction != null) {
         allActions = allActions.concatWith((Publisher)writeAction.get());
      }

      return allActions.then();
   }

   protected abstract Mono<Void> writeWithInternal(Publisher<? extends DataBuffer> body);

   protected abstract Mono<Void> writeAndFlushWithInternal(Publisher<? extends Publisher<? extends DataBuffer>> body);

   protected abstract void applyStatusCode();

   protected abstract void applyHeaders();

   protected abstract void applyCookies();

   protected void touchDataBuffer(DataBuffer buffer) {
   }

   private enum State {
      NEW,
      COMMITTING,
      COMMIT_ACTION_FAILED,
      COMMITTED;
   }
}
