package org.springframework.http.converter.multipart;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;

public class MultipartHttpMessageConverter implements SmartHttpMessageConverter<MultiValueMap<String, ?>> {
   private final List<HttpMessageConverter<?>> partConverters;
   private @Nullable Path tempDirectory;
   private List<MediaType> supportedMediaTypes = new ArrayList<>();
   private Charset charset = StandardCharsets.UTF_8;
   private @Nullable Charset multipartCharset;
   private int maxInMemorySize = 262144;
   private int maxHeadersSize = 10240;
   private long maxDiskUsagePerPart = -1L;
   private int maxParts = -1;

   public MultipartHttpMessageConverter(Iterable<HttpMessageConverter<?>> converters) {
      this.supportedMediaTypes.add(MediaType.MULTIPART_FORM_DATA);
      this.supportedMediaTypes.add(MediaType.MULTIPART_MIXED);
      this.supportedMediaTypes.add(MediaType.MULTIPART_RELATED);
      this.partConverters = new ArrayList<>();
      converters.forEach(this.partConverters::add);
      this.applyDefaultCharset();
   }

   public MultipartHttpMessageConverter() {
      this(List.of(new ByteArrayHttpMessageConverter(), new StringHttpMessageConverter(), new ResourceHttpMessageConverter()));
   }

   public void setSupportedMediaTypes(List<MediaType> supportedMediaTypes) {
      Assert.notNull(supportedMediaTypes, "'supportedMediaTypes' must not be null");
      this.supportedMediaTypes = new ArrayList<>(supportedMediaTypes);
   }

   public void addSupportedMediaTypes(MediaType... supportedMediaTypes) {
      Assert.notNull(supportedMediaTypes, "'supportedMediaTypes' must not be null");
      Assert.noNullElements(supportedMediaTypes, "'supportedMediaTypes' must not contain null elements");
      Collections.addAll(this.supportedMediaTypes, supportedMediaTypes);
   }

   @Override
   public List<MediaType> getSupportedMediaTypes() {
      return Collections.unmodifiableList(this.supportedMediaTypes);
   }

   public List<HttpMessageConverter<?>> getPartConverters() {
      return Collections.unmodifiableList(this.partConverters);
   }

   public void setCharset(@Nullable Charset charset) {
      if (charset != this.charset) {
         this.charset = charset != null ? charset : StandardCharsets.UTF_8;
         this.applyDefaultCharset();
      }
   }

   private void applyDefaultCharset() {
      for (HttpMessageConverter<?> candidate : this.partConverters) {
         if (candidate instanceof AbstractHttpMessageConverter<?> converter && converter.getDefaultCharset() != null) {
            converter.setDefaultCharset(this.charset);
         }
      }
   }

   public void setMultipartCharset(Charset charset) {
      this.multipartCharset = charset;
   }

   public void setMaxHeadersSize(int byteCount) {
      this.maxHeadersSize = byteCount;
   }

   public void setMaxInMemorySize(int maxInMemorySize) {
      this.maxInMemorySize = maxInMemorySize;
   }

   public void setMaxDiskUsagePerPart(long maxDiskUsagePerPart) {
      this.maxDiskUsagePerPart = maxDiskUsagePerPart;
   }

   public void setMaxParts(int maxParts) {
      this.maxParts = maxParts;
   }

   @Override
   public boolean canRead(ResolvableType elementType, @Nullable MediaType mediaType) {
      return !this.supportsMediaType(mediaType)
         ? false
         : MultiValueMap.class.isAssignableFrom(elementType.toClass())
            && (elementType.hasUnresolvableGenerics() || Part.class.isAssignableFrom(elementType.getGeneric(new int[]{1}).toClass()));
   }

   private boolean supportsMediaType(@Nullable MediaType mediaType) {
      if (mediaType == null) {
         return true;
      }

      for (MediaType supportedMediaType : this.getSupportedMediaTypes()) {
         if (supportedMediaType.includes(mediaType)) {
            return true;
         }
      }

      return false;
   }

   public MultiValueMap<String, Part> read(ResolvableType type, HttpInputMessage message, @Nullable Map<String, Object> hints) throws IOException, HttpMessageNotReadableException {
      Charset headersCharset = MultipartUtils.charset(message.getHeaders());
      byte[] boundary = boundary(message, headersCharset);
      if (boundary == null) {
         throw new HttpMessageNotReadableException("No multipart boundary found in Content-Type: \"" + message.getHeaders().getContentType() + "\"", message);
      }

      PartGenerator partGenerator = new PartGenerator(this.maxInMemorySize, this.maxDiskUsagePerPart, this.maxParts, this.getTempDirectory());
      MultipartParser.parse(message.getBody(), boundary, headersCharset, this.maxHeadersSize, 2048, partGenerator);
      return partGenerator.getParts();
   }

   private static byte @Nullable [] boundary(HttpInputMessage message, Charset headersCharset) {
      MediaType contentType = message.getHeaders().getContentType();
      if (contentType != null) {
         String boundary = contentType.getParameter("boundary");
         if (boundary != null) {
            int len = boundary.length();
            if (len > 2 && boundary.charAt(0) == '"' && boundary.charAt(len - 1) == '"') {
               boundary = boundary.substring(1, len - 1);
            }

            return boundary.getBytes(headersCharset);
         }
      }

      return null;
   }

   private Path getTempDirectory() throws IOException {
      if (this.tempDirectory == null || !this.tempDirectory.toFile().exists()) {
         this.tempDirectory = Files.createTempDirectory("spring-multipart-");
      }

      return this.tempDirectory;
   }

   @Override
   public boolean canWrite(ResolvableType targetType, Class<?> valueClass, @Nullable MediaType mediaType) {
      if (!MultiValueMap.class.isAssignableFrom(targetType.toClass())) {
         return false;
      }

      if (mediaType != null && !MediaType.ALL.equals(mediaType)) {
         for (MediaType supportedMediaType : this.getSupportedMediaTypes()) {
            if (supportedMediaType.isCompatibleWith(mediaType)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public void write(
      MultiValueMap<String, ?> map, ResolvableType type, @Nullable MediaType contentType, HttpOutputMessage outputMessage, @Nullable Map<String, Object> hints
   ) throws IOException, HttpMessageNotWritableException {
      final MultiValueMap<String, Object> parts = (MultiValueMap<String, Object>)map;
      if (contentType == null) {
         contentType = MediaType.MULTIPART_FORM_DATA;
      }

      Map<String, String> parameters = new LinkedHashMap<>(contentType.getParameters().size() + 2);
      parameters.putAll(contentType.getParameters());
      final byte[] boundary = MimeTypeUtils.generateMultipartBoundary();
      if (!this.isFilenameCharsetSet() && !this.charset.equals(StandardCharsets.UTF_8) && !this.charset.equals(StandardCharsets.US_ASCII)) {
         parameters.put("charset", this.charset.name());
      }

      parameters.put("boundary", new String(boundary, StandardCharsets.US_ASCII));
      contentType = new MediaType(contentType, parameters);
      outputMessage.getHeaders().setContentType(contentType);
      if (outputMessage instanceof StreamingHttpOutputMessage streamingOutputMessage) {
         final boolean repeatable = this.checkPartsRepeatable(parts, contentType);
         streamingOutputMessage.setBody(new StreamingHttpOutputMessage.Body() {
            @Override
            public void writeTo(OutputStream outputStream) throws IOException {
               MultipartHttpMessageConverter.this.writeParts(outputStream, parts, boundary);
               MultipartHttpMessageConverter.writeEnd(outputStream, boundary);
            }

            @Override
            public boolean repeatable() {
               return repeatable;
            }
         });
      } else {
         this.writeParts(outputMessage.getBody(), parts, boundary);
         writeEnd(outputMessage.getBody(), boundary);
      }
   }

   private <T> boolean checkPartsRepeatable(MultiValueMap<String, Object> map, MediaType contentType) {
      return map.entrySet()
         .stream()
         .allMatch(
            entry -> ((List)entry.getValue())
               .stream()
               .filter(Objects::nonNull)
               .allMatch(part -> this.isPartRepeatable((String)entry.getKey(), part, contentType))
         );
   }

   private <T> boolean isPartRepeatable(String name, Object part, MediaType contentType) {
      HttpHeaders headers = null;
      Object body = part;
      if (part instanceof HttpEntity<?> entity) {
         headers = entity.getHeaders();
         body = entity.getBody();
         Assert.state(body != null, "Empty body for part '" + name + "': " + part);
      }

      HttpMessageConverter<T> converter = (HttpMessageConverter<T>)this.findConverterFor(name, headers, body);
      return converter != null && converter.canWriteRepeatedly((T)body, contentType);
   }

   private @Nullable HttpMessageConverter<?> findConverterFor(String name, @Nullable HttpHeaders headers, Object body) {
      Class<?> partType = body.getClass();
      MediaType contentType = headers != null ? headers.getContentType() : null;

      for (HttpMessageConverter<?> converter : this.partConverters) {
         if (converter.canWrite(partType, contentType)) {
            return converter;
         }
      }

      return null;
   }

   private boolean isFilenameCharsetSet() {
      return this.multipartCharset != null;
   }

   private void writeParts(OutputStream os, MultiValueMap<String, Object> parts, byte[] boundary) throws IOException {
      for (Entry<String, List<Object>> entry : parts.entrySet()) {
         String name = entry.getKey();

         for (Object part : entry.getValue()) {
            if (part != null) {
               this.writeBoundary(os, boundary);
               this.writePart(name, this.getHttpEntity(part), os);
               writeNewLine(os);
            }
         }
      }
   }

   private void writePart(String name, HttpEntity<?> partEntity, OutputStream os) throws IOException {
      Object partBody = partEntity.getBody();
      Assert.state(partBody != null, "Empty body for part '" + name + "': " + partEntity);
      HttpHeaders partHeaders = partEntity.getHeaders();
      MediaType partContentType = partHeaders.getContentType();
      HttpMessageConverter<?> converter = this.findConverterFor(name, partHeaders, partBody);
      if (converter != null) {
         Charset charset = this.isFilenameCharsetSet() ? StandardCharsets.US_ASCII : this.charset;
         HttpOutputMessage multipartMessage = new MultipartHttpMessageConverter.MultipartHttpOutputMessage(os, charset);
         String filename = this.getFilename(partBody);
         ContentDisposition.Builder cd = ContentDisposition.formData().name(name);
         if (filename != null) {
            cd.filename(filename, this.multipartCharset);
         }

         multipartMessage.getHeaders().setContentDisposition(cd.build());
         if (!partHeaders.isEmpty()) {
            multipartMessage.getHeaders().putAll(partHeaders);
         }

         ((HttpMessageConverter<Object>)converter).write(partBody, partContentType, multipartMessage);
      } else {
         throw new HttpMessageNotWritableException(
            "Could not write request: no suitable HttpMessageConverter found for request type [" + partBody.getClass().getName() + "]"
         );
      }
   }

   protected HttpEntity<?> getHttpEntity(Object part) {
      return part instanceof HttpEntity<?> httpEntity ? httpEntity : new HttpEntity<>(part);
   }

   protected @Nullable String getFilename(Object part) {
      return part instanceof Resource resource ? resource.getFilename() : null;
   }

   private void writeBoundary(OutputStream os, byte[] boundary) throws IOException {
      os.write(45);
      os.write(45);
      os.write(boundary);
      writeNewLine(os);
   }

   private static void writeEnd(OutputStream os, byte[] boundary) throws IOException {
      os.write(45);
      os.write(45);
      os.write(boundary);
      os.write(45);
      os.write(45);
      writeNewLine(os);
   }

   private static void writeNewLine(OutputStream os) throws IOException {
      os.write(13);
      os.write(10);
   }

   private static class MultipartHttpOutputMessage implements HttpOutputMessage {
      private final OutputStream outputStream;
      private final Charset charset;
      private final HttpHeaders headers = new HttpHeaders();
      private boolean headersWritten = false;

      public MultipartHttpOutputMessage(OutputStream outputStream, Charset charset) {
         this.outputStream = new MultipartHttpMessageConverter.MultipartOutputStream(outputStream);
         this.charset = charset;
      }

      @Override
      public HttpHeaders getHeaders() {
         return this.headersWritten ? HttpHeaders.readOnlyHttpHeaders(this.headers) : this.headers;
      }

      @Override
      public OutputStream getBody() throws IOException {
         this.writeHeaders();
         return this.outputStream;
      }

      private void writeHeaders() throws IOException {
         if (!this.headersWritten) {
            for (Entry<String, List<String>> entry : this.headers.headerSet()) {
               byte[] headerName = this.getBytes(entry.getKey());

               for (String headerValueString : entry.getValue()) {
                  byte[] headerValue = this.getBytes(headerValueString);
                  this.outputStream.write(headerName);
                  this.outputStream.write(58);
                  this.outputStream.write(32);
                  this.outputStream.write(headerValue);
                  MultipartHttpMessageConverter.writeNewLine(this.outputStream);
               }
            }

            MultipartHttpMessageConverter.writeNewLine(this.outputStream);
            this.headersWritten = true;
         }
      }

      private byte[] getBytes(String name) {
         return name.getBytes(this.charset);
      }
   }

   private static class MultipartOutputStream extends FilterOutputStream {
      public MultipartOutputStream(OutputStream out) {
         super(out);
      }

      @Override
      public void write(byte[] b, int off, int let) throws IOException {
         this.out.write(b, off, let);
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
   }
}
