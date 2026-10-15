package org.springframework.http.converter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;

public class StringHttpMessageConverter extends AbstractHttpMessageConverter<String> {
   private static final MediaType APPLICATION_PLUS_JSON = new MediaType("application", "*+json");
   public static final Charset DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;
   private volatile @Nullable List<Charset> availableCharsets;
   private boolean writeAcceptCharset = false;

   public StringHttpMessageConverter() {
      this(DEFAULT_CHARSET);
   }

   public StringHttpMessageConverter(Charset defaultCharset) {
      super(defaultCharset, MediaType.TEXT_PLAIN, MediaType.ALL);
   }

   public void setWriteAcceptCharset(boolean writeAcceptCharset) {
      this.writeAcceptCharset = writeAcceptCharset;
   }

   @Override
   public boolean supports(Class<?> clazz) {
      return String.class == clazz;
   }

   protected String readInternal(Class<? extends String> clazz, HttpInputMessage inputMessage) throws IOException {
      Charset charset = this.getContentTypeCharset(inputMessage.getHeaders().getContentType());
      long length = inputMessage.getHeaders().getContentLength();
      byte[] bytes = length >= 0L && length <= 2147483647L ? inputMessage.getBody().readNBytes((int)length) : inputMessage.getBody().readAllBytes();
      return new String(bytes, charset);
   }

   protected void writeInternal(String str, HttpOutputMessage outputMessage) throws IOException {
      HttpHeaders headers = outputMessage.getHeaders();
      if (this.writeAcceptCharset && headers.get("Accept-Charset") == null) {
         headers.setAcceptCharset(this.getAcceptedCharsets());
      }

      Charset charset = this.getContentTypeCharset(headers.getContentType());
      outputMessage.getBody().write(str.getBytes(charset));
   }

   protected void addDefaultHeaders(HttpHeaders headers, String s, @Nullable MediaType type) throws IOException {
      if (headers.getContentType() == null
         && type != null
         && type.isConcrete()
         && (type.isCompatibleWith(MediaType.APPLICATION_JSON) || type.isCompatibleWith(APPLICATION_PLUS_JSON))) {
         headers.setContentType(type);
      }

      super.addDefaultHeaders(headers, s, type);
   }

   protected Long getContentLength(String str, @Nullable MediaType contentType) {
      Charset charset = this.getContentTypeCharset(contentType);
      return (long)str.getBytes(charset).length;
   }

   protected List<Charset> getAcceptedCharsets() {
      List<Charset> charsets = this.availableCharsets;
      if (charsets == null) {
         charsets = new ArrayList<>(Charset.availableCharsets().values());
         this.availableCharsets = charsets;
      }

      return charsets;
   }

   private Charset getContentTypeCharset(@Nullable MediaType contentType) {
      if (contentType != null) {
         Charset charset = contentType.getCharset();
         if (charset != null) {
            return charset;
         }

         if (contentType.isCompatibleWith(MediaType.APPLICATION_JSON) || contentType.isCompatibleWith(APPLICATION_PLUS_JSON)) {
            return StandardCharsets.UTF_8;
         }
      }

      Charset charset = this.getDefaultCharset();
      Assert.state(charset != null, "No default charset");
      return charset;
   }

   public boolean canWriteRepeatedly(String s, @Nullable MediaType contentType) {
      return true;
   }

   protected boolean supportsRepeatableWrites(String s) {
      return true;
   }
}
