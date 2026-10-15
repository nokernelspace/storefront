package org.springframework.http.client;

import java.io.IOException;
import java.io.OutputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.util.Assert;
import org.springframework.util.FastByteArrayOutputStream;

public abstract class AbstractStreamingClientHttpRequest extends AbstractClientHttpRequest implements StreamingHttpOutputMessage {
   private StreamingHttpOutputMessage.@Nullable Body body;
   private @Nullable FastByteArrayOutputStream bodyStream;

   @Override
   protected final OutputStream getBodyInternal(HttpHeaders headers) {
      Assert.state(this.body == null, "Invoke either getBody or setBody; not both");
      if (this.bodyStream == null) {
         this.bodyStream = new FastByteArrayOutputStream(1024);
      }

      return this.bodyStream;
   }

   @Override
   public final void setBody(StreamingHttpOutputMessage.Body body) {
      Assert.notNull(body, "Body must not be null");
      this.assertNotExecuted();
      Assert.state(this.bodyStream == null, "Invoke either getBody or setBody; not both");
      this.body = body;
   }

   @Override
   protected final ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
      if (this.body == null && this.bodyStream != null) {
         this.body = outputStream -> this.bodyStream.writeTo(outputStream);
      }

      return this.executeInternal(headers, this.body);
   }

   protected abstract ClientHttpResponse executeInternal(HttpHeaders headers, StreamingHttpOutputMessage.@Nullable Body body) throws IOException;
}
