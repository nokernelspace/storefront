package org.springframework.http.client;

import java.io.IOException;
import java.io.OutputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.StreamUtils;

public abstract class AbstractBufferingClientHttpRequest extends AbstractClientHttpRequest {
   private final FastByteArrayOutputStream bufferedOutput = new FastByteArrayOutputStream(1024);

   @Override
   protected final OutputStream getBodyInternal(HttpHeaders headers) {
      return this.bufferedOutput;
   }

   @Override
   protected final ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
      byte[] bytes = this.bufferedOutput.toByteArrayUnsafe();
      if (headers.getContentLength() < 0L) {
         headers.setContentLength(bytes.length);
      }

      ClientHttpResponse result = this.executeInternal(headers, bytes);
      this.bufferedOutput.reset();
      return result;
   }

   protected abstract ClientHttpResponse executeInternal(HttpHeaders headers, byte[] bufferedOutput) throws IOException;

   final ClientHttpResponse executeWithRequestAndBody(ClientHttpRequest request, byte[] bufferedOutput, boolean bufferResponse) throws IOException {
      if (bufferedOutput.length > 0) {
         long contentLength = request.getHeaders().getContentLength();
         if (contentLength > -1L && contentLength != bufferedOutput.length) {
            request.getHeaders().setContentLength(bufferedOutput.length);
         }

         if (request instanceof StreamingHttpOutputMessage streamingOutputMessage) {
            streamingOutputMessage.setBody(bufferedOutput);
         } else {
            StreamUtils.copy(bufferedOutput, request.getBody());
         }
      }

      ClientHttpResponse response = request.execute();
      return bufferResponse ? new BufferingClientHttpResponseWrapper(response) : response;
   }
}
