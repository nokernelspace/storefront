package org.springframework.http.codec.multipart;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;

abstract class DefaultPartEvents {
   public static FormPartEvent form(HttpHeaders headers) {
      Assert.notNull(headers, "Headers must not be null");
      return new DefaultPartEvents.DefaultFormFieldPartEvent(headers);
   }

   public static FormPartEvent form(HttpHeaders headers, String value) {
      Assert.notNull(headers, "Headers must not be null");
      Assert.notNull(value, "Value must not be null");
      return new DefaultPartEvents.DefaultFormFieldPartEvent(headers, value);
   }

   public static FilePartEvent file(HttpHeaders headers, DataBuffer dataBuffer, boolean isLast) {
      Assert.notNull(headers, "Headers must not be null");
      Assert.notNull(dataBuffer, "DataBuffer must not be null");
      return new DefaultPartEvents.DefaultFilePartEvent(headers, dataBuffer, isLast);
   }

   public static FilePartEvent file(HttpHeaders headers) {
      Assert.notNull(headers, "Headers must not be null");
      return new DefaultPartEvents.DefaultFilePartEvent(headers);
   }

   public static PartEvent create(HttpHeaders headers, DataBuffer dataBuffer, boolean isLast) {
      Assert.notNull(headers, "Headers must not be null");
      Assert.notNull(dataBuffer, "DataBuffer must not be null");
      return headers.getContentDisposition().getFilename() != null
         ? file(headers, dataBuffer, isLast)
         : new DefaultPartEvents.DefaultPartEvent(headers, dataBuffer, isLast);
   }

   public static PartEvent create(HttpHeaders headers) {
      Assert.notNull(headers, "Headers must not be null");
      return headers.getContentDisposition().getFilename() != null ? file(headers) : new DefaultPartEvents.DefaultPartEvent(headers);
   }

   private abstract static class AbstractPartEvent implements PartEvent {
      private final HttpHeaders headers;

      protected AbstractPartEvent(HttpHeaders headers) {
         this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
      }

      @Override
      public HttpHeaders headers() {
         return this.headers;
      }
   }

   private static class DefaultFilePartEvent extends DefaultPartEvents.DefaultPartEvent implements FilePartEvent {
      public DefaultFilePartEvent(HttpHeaders headers) {
         super(headers);
      }

      public DefaultFilePartEvent(HttpHeaders headers, DataBuffer content, boolean last) {
         super(headers, content, last);
      }
   }

   private static final class DefaultFormFieldPartEvent extends DefaultPartEvents.AbstractPartEvent implements FormPartEvent {
      private static final String EMPTY = "";
      private final String value;

      public DefaultFormFieldPartEvent(HttpHeaders headers) {
         this(headers, "");
      }

      public DefaultFormFieldPartEvent(HttpHeaders headers, String value) {
         super(headers);
         this.value = value;
      }

      @Override
      public String value() {
         return this.value;
      }

      @Override
      public DataBuffer content() {
         byte[] bytes = this.value.getBytes(MultipartUtils.charset(this.headers()));
         return DefaultDataBufferFactory.sharedInstance.wrap(bytes);
      }

      @Override
      public boolean isLast() {
         return true;
      }
   }

   private static class DefaultPartEvent extends DefaultPartEvents.AbstractPartEvent {
      private static final DataBuffer EMPTY = DefaultDataBufferFactory.sharedInstance.allocateBuffer(0);
      private final DataBuffer content;
      private final boolean last;

      public DefaultPartEvent(HttpHeaders headers) {
         this(headers, EMPTY, true);
      }

      public DefaultPartEvent(HttpHeaders headers, DataBuffer content, boolean last) {
         super(headers);
         this.content = content;
         this.last = last;
      }

      @Override
      public DataBuffer content() {
         return this.content;
      }

      @Override
      public boolean isLast() {
         return this.last;
      }
   }
}
