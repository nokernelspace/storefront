package org.springframework.http.client;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

class JdkClientHttpRequest extends AbstractStreamingClientHttpRequest {
   private static final OutputStreamPublisher.ByteMapper<ByteBuffer> BYTE_MAPPER = new JdkClientHttpRequest.ByteBufferMapper();
   private static final Set<String> DISALLOWED_HEADERS = disallowedHeaders();
   private static final List<String> SUPPORTED_ENCODINGS = List.of("gzip", "deflate");
   private final HttpClient httpClient;
   private final HttpMethod method;
   private final URI uri;
   private final Executor executor;
   private final @Nullable Duration timeout;
   private final boolean compression;

   JdkClientHttpRequest(HttpClient httpClient, URI uri, HttpMethod method, Executor executor, @Nullable Duration readTimeout, boolean compression) {
      this.httpClient = httpClient;
      this.uri = uri;
      this.method = method;
      this.executor = executor;
      this.timeout = readTimeout;
      this.compression = compression;
   }

   @Override
   public HttpMethod getMethod() {
      return this.method;
   }

   @Override
   public URI getURI() {
      return this.uri;
   }

   @Override
   protected ClientHttpResponse executeInternal(HttpHeaders headers, StreamingHttpOutputMessage.@Nullable Body body) throws IOException {
      CompletableFuture<HttpResponse<InputStream>> responseFuture = null;
      JdkClientHttpRequest.TimeoutHandler timeoutHandler = null;

      try {
         HttpRequest request = this.buildRequest(headers, body);
         responseFuture = this.httpClient
            .sendAsync(request, this.compression ? new JdkClientHttpRequest.DecompressingBodyHandler() : BodyHandlers.ofInputStream());
         if (this.timeout != null) {
            timeoutHandler = new JdkClientHttpRequest.TimeoutHandler(responseFuture, this.timeout);
            HttpResponse<InputStream> response = responseFuture.get();
            InputStream inputStream = timeoutHandler.wrapInputStream(response);
            return new JdkClientHttpResponse(response, this.processResponseHeaders(), inputStream);
         } else {
            HttpResponse<InputStream> response = responseFuture.get();
            return new JdkClientHttpResponse(response, this.processResponseHeaders(), response.body());
         }
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
         responseFuture.cancel(true);
         throw new IOException("Request was interrupted: " + ex.getMessage(), ex);
      } catch (ExecutionException ex) {
         Throwable cause = ex.getCause();
         if (cause instanceof CancellationException ce) {
            if (timeoutHandler != null) {
               timeoutHandler.handleCancellationException(ce);
            }

            throw new IOException("Request cancelled", cause);
         } else if (cause instanceof UncheckedIOException uioEx) {
            throw uioEx.getCause();
         } else if (cause instanceof RuntimeException rtEx) {
            throw rtEx;
         } else if (cause instanceof IOException ioEx) {
            throw ioEx;
         } else {
            String message = cause == null ? null : cause.getMessage();
            throw message == null ? new IOException(cause) : new IOException(message, cause);
         }
      } catch (CancellationException ex) {
         if (timeoutHandler != null) {
            timeoutHandler.handleCancellationException(ex);
         }

         throw new IOException("Request cancelled", ex);
      }
   }

   private HttpRequest buildRequest(HttpHeaders headers, StreamingHttpOutputMessage.@Nullable Body body) {
      Builder builder = HttpRequest.newBuilder().uri(this.uri);
      if (this.compression && !headers.containsHeader("Accept-Encoding")) {
         headers.addAll("Accept-Encoding", SUPPORTED_ENCODINGS);
      }

      headers.forEach((headerName, headerValues) -> {
         if (!DISALLOWED_HEADERS.contains(headerName.toLowerCase(Locale.ROOT))) {
            for (String headerValue : headerValues) {
               builder.header(headerName, headerValue != null ? headerValue : "");
            }
         }
      });
      if (body != null) {
         builder.method(this.method.name(), this.bodyPublisher(headers, body));
      } else {
         switch (this.method.name()) {
            case "GET":
               builder.GET();
               break;
            case "DELETE":
               builder.DELETE();
               break;
            default:
               builder.method(this.method.name(), BodyPublishers.noBody());
         }
      }

      return builder.build();
   }

   private BodyPublisher bodyPublisher(HttpHeaders headers, StreamingHttpOutputMessage.Body body) {
      Publisher<ByteBuffer> publisher = new OutputStreamPublisher<>(os -> body.writeTo(StreamUtils.nonClosing(os)), BYTE_MAPPER, this.executor, null);
      long contentLength = headers.getContentLength();
      if (contentLength > 0L) {
         return BodyPublishers.fromPublisher(publisher, contentLength);
      } else {
         return contentLength == 0L ? BodyPublishers.noBody() : BodyPublishers.fromPublisher(publisher);
      }
   }

   private static Set<String> disallowedHeaders() {
      TreeSet<String> headers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      headers.addAll(Set.of("connection", "content-length", "expect", "host", "upgrade"));
      String headersToAllow = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
      if (headersToAllow != null) {
         Set<String> toAllow = StringUtils.commaDelimitedListToSet(headersToAllow);
         headers.removeAll(toAllow);
      }

      return Collections.unmodifiableSet(headers);
   }

   private Consumer<HttpHeaders> processResponseHeaders() {
      return this.compression ? headers -> {
         String encoding = headers.getFirst("Content-Encoding");
         if (encoding != null && SUPPORTED_ENCODINGS.contains(encoding)) {
            headers.remove("Content-Encoding");
            headers.remove("Content-Length");
         }
      } : headers -> {};
   }

   private static final class ByteBufferMapper implements OutputStreamPublisher.ByteMapper<ByteBuffer> {
      public ByteBuffer map(int b) {
         ByteBuffer byteBuffer = ByteBuffer.allocate(1);
         byteBuffer.put((byte)b);
         byteBuffer.flip();
         return byteBuffer;
      }

      public ByteBuffer map(byte[] b, int off, int len) {
         ByteBuffer byteBuffer = ByteBuffer.allocate(len);
         byteBuffer.put(b, off, len);
         byteBuffer.flip();
         return byteBuffer;
      }
   }

   private static final class DecompressingBodyHandler implements BodyHandler<InputStream> {
      @Override
      public BodySubscriber<InputStream> apply(ResponseInfo responseInfo) {
         String contentEncoding = responseInfo.headers().firstValue("Content-Encoding").orElse("").toLowerCase(Locale.ROOT);

         return switch (contentEncoding) {
            case "gzip", "deflate" -> BodySubscribers.mapping(BodySubscribers.ofInputStream(), is -> decompressStream(is, contentEncoding));
            default -> BodySubscribers.ofInputStream();
         };
      }

      private static InputStream decompressStream(InputStream original, String contentEncoding) {
         PushbackInputStream wrapped = new PushbackInputStream(original);

         try {
            if (hasResponseBody(wrapped)) {
               if (contentEncoding.equals("gzip")) {
                  return new GZIPInputStream(wrapped);
               } else {
                  return contentEncoding.equals("deflate") ? new InflaterInputStream(wrapped) : wrapped;
               }
            } else {
               return wrapped;
            }
         } catch (IOException ex) {
            throw new UncheckedIOException(ex);
         }
      }

      private static boolean hasResponseBody(PushbackInputStream inputStream) {
         try {
            int b = inputStream.read();
            if (b == -1) {
               return false;
            }

            inputStream.unread(b);
            return true;
         } catch (IOException exc) {
            return false;
         }
      }
   }

   private static final class TimeoutHandler {
      private final CompletableFuture<Void> timeoutFuture;
      private final AtomicBoolean timeout = new AtomicBoolean(false);

      private TimeoutHandler(CompletableFuture<HttpResponse<InputStream>> future, Duration timeout) {
         this.timeoutFuture = new CompletableFuture<Void>().completeOnTimeout(null, timeout.toMillis(), TimeUnit.MILLISECONDS);
         this.timeoutFuture.thenRun(() -> {
            this.timeout.set(true);
            if (!future.cancel(true) && !future.isCompletedExceptionally() && future.isDone()) {
               try {
                  future.get().body().close();
               } catch (Exception var3) {
               }
            }
         });
      }

      public @Nullable InputStream wrapInputStream(HttpResponse<InputStream> response) {
         InputStream body = response.body();
         return body == null ? body : new FilterInputStream(body) {
            @Override
            public void close() throws IOException {
               TimeoutHandler.this.timeoutFuture.cancel(false);
               super.close();
            }
         };
      }

      public void handleCancellationException(CancellationException ex) throws HttpTimeoutException {
         if (this.timeout.get()) {
            throw new HttpTimeoutException(ex.getMessage());
         }
      }
   }
}
