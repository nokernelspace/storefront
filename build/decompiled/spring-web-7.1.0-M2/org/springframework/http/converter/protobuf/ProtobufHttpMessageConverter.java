package org.springframework.http.converter.protobuf;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import com.google.protobuf.util.JsonFormat.Printer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

public class ProtobufHttpMessageConverter extends AbstractHttpMessageConverter<Message> {
   public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
   public static final MediaType PROTOBUF = new MediaType("application", "x-protobuf", DEFAULT_CHARSET);
   public static final MediaType PLUS_PROTOBUF = new MediaType("application", "*+x-protobuf", DEFAULT_CHARSET);
   public static final String X_PROTOBUF_SCHEMA_HEADER = "X-Protobuf-Schema";
   public static final String X_PROTOBUF_MESSAGE_HEADER = "X-Protobuf-Message";
   private static final boolean PROTOBUF_JSON_FORMAT_PRESENT = ClassUtils.isPresent(
      "com.google.protobuf.util.JsonFormat", ProtobufHttpMessageConverter.class.getClassLoader()
   );
   private static final Map<Class<?>, Method> methodCache = new ConcurrentReferenceHashMap();
   private final ProtobufHttpMessageConverter.@Nullable ProtobufFormatDelegate protobufFormatDelegate;
   final ExtensionRegistry extensionRegistry;

   public ProtobufHttpMessageConverter() {
      this(null, null);
   }

   public ProtobufHttpMessageConverter(ExtensionRegistry extensionRegistry) {
      this(null, extensionRegistry);
   }

   protected ProtobufHttpMessageConverter(
      ProtobufHttpMessageConverter.@Nullable ProtobufFormatDelegate formatDelegate, @Nullable ExtensionRegistry extensionRegistry
   ) {
      if (formatDelegate != null) {
         this.protobufFormatDelegate = formatDelegate;
      } else if (PROTOBUF_JSON_FORMAT_PRESENT) {
         this.protobufFormatDelegate = new ProtobufHttpMessageConverter.ProtobufJavaUtilDelegate(null, null);
      } else {
         this.protobufFormatDelegate = null;
      }

      this.setSupportedMediaTypes(
         Arrays.asList(
            this.protobufFormatDelegate != null
               ? this.protobufFormatDelegate.supportedMediaTypes()
               : new MediaType[]{PROTOBUF, PLUS_PROTOBUF, MediaType.TEXT_PLAIN}
         )
      );
      this.extensionRegistry = extensionRegistry == null ? ExtensionRegistry.newInstance() : extensionRegistry;
   }

   @Override
   protected boolean supports(Class<?> clazz) {
      return Message.class.isAssignableFrom(clazz);
   }

   protected MediaType getDefaultContentType(Message message) {
      return PROTOBUF;
   }

   protected Message readInternal(Class<? extends Message> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      MediaType contentType = inputMessage.getHeaders().getContentType();
      if (contentType == null) {
         contentType = PROTOBUF;
      }

      Charset charset = contentType.getCharset();
      if (charset == null) {
         charset = DEFAULT_CHARSET;
      }

      Builder builder = this.getMessageBuilder(clazz);
      if (PROTOBUF.isCompatibleWith(contentType) || PLUS_PROTOBUF.isCompatibleWith(contentType)) {
         builder.mergeFrom(inputMessage.getBody(), this.extensionRegistry);
      } else if (MediaType.TEXT_PLAIN.isCompatibleWith(contentType)) {
         InputStreamReader reader = new InputStreamReader(inputMessage.getBody(), charset);
         TextFormat.merge(reader, this.extensionRegistry, builder);
      } else if (this.protobufFormatDelegate != null) {
         this.protobufFormatDelegate.merge(inputMessage, contentType, charset, builder, this.extensionRegistry);
      }

      return builder.build();
   }

   private Builder getMessageBuilder(Class<? extends Message> clazz) {
      try {
         Method method = methodCache.get(clazz);
         if (method == null) {
            method = clazz.getMethod("newBuilder");
            methodCache.put(clazz, method);
         }

         return (Builder)method.invoke(clazz);
      } catch (Exception ex) {
         throw new HttpMessageConversionException("Invalid Protobuf Message type: no invocable newBuilder() method on " + clazz, ex);
      }
   }

   @Override
   protected boolean canWrite(@Nullable MediaType mediaType) {
      return super.canWrite(mediaType) || this.protobufFormatDelegate != null && this.protobufFormatDelegate.supportsWriteOnly(mediaType);
   }

   protected void writeInternal(Message message, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      MediaType contentType = outputMessage.getHeaders().getContentType();
      if (contentType == null) {
         contentType = this.getDefaultContentType(message);
      }

      Charset charset = contentType.getCharset();
      if (charset == null) {
         charset = DEFAULT_CHARSET;
      }

      if (PROTOBUF.isCompatibleWith(contentType) || PLUS_PROTOBUF.isCompatibleWith(contentType)) {
         this.setProtoHeader(outputMessage, message);
         CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputMessage.getBody());
         message.writeTo(codedOutputStream);
         codedOutputStream.flush();
      } else if (MediaType.TEXT_PLAIN.isCompatibleWith(contentType)) {
         OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputMessage.getBody(), charset);
         TextFormat.printer().print(message, outputStreamWriter);
         outputStreamWriter.flush();
      } else if (this.protobufFormatDelegate != null) {
         this.protobufFormatDelegate.print(message, outputMessage, contentType, charset);
      }
   }

   private void setProtoHeader(HttpOutputMessage response, Message message) {
      response.getHeaders().set("X-Protobuf-Schema", message.getDescriptorForType().getFile().getName());
      response.getHeaders().set("X-Protobuf-Message", message.getDescriptorForType().getFullName());
   }

   public boolean canWriteRepeatedly(Message message, @Nullable MediaType contentType) {
      return true;
   }

   protected boolean supportsRepeatableWrites(Message message) {
      return true;
   }

   protected interface ProtobufFormatDelegate {
      MediaType[] supportedMediaTypes();

      boolean supportsWriteOnly(@Nullable MediaType mediaType);

      void merge(HttpInputMessage inputMessage, MediaType contentType, Charset charset, Builder builder, ExtensionRegistry extensionRegistry) throws IOException, HttpMessageConversionException;

      void print(Message message, HttpOutputMessage outputMessage, MediaType contentType, Charset charset) throws IOException, HttpMessageConversionException;
   }

   static class ProtobufJavaUtilDelegate implements ProtobufHttpMessageConverter.ProtobufFormatDelegate {
      private final Parser parser;
      private final Printer printer;

      public ProtobufJavaUtilDelegate(@Nullable Parser parser, @Nullable Printer printer) {
         this.parser = parser != null ? parser : JsonFormat.parser();
         this.printer = printer != null ? printer : JsonFormat.printer();
      }

      @Override
      public MediaType[] supportedMediaTypes() {
         return new MediaType[]{
            ProtobufHttpMessageConverter.PROTOBUF, ProtobufHttpMessageConverter.PLUS_PROTOBUF, MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON
         };
      }

      @Override
      public boolean supportsWriteOnly(@Nullable MediaType mediaType) {
         return false;
      }

      @Override
      public void merge(HttpInputMessage inputMessage, MediaType contentType, Charset charset, Builder builder, ExtensionRegistry extensionRegistry) throws IOException, HttpMessageConversionException {
         if (contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            InputStreamReader reader = new InputStreamReader(inputMessage.getBody(), charset);
            this.parser.merge(reader, builder);
         } else {
            throw new HttpMessageConversionException("protobuf-java-util does not support parsing " + contentType);
         }
      }

      @Override
      public void print(Message message, HttpOutputMessage outputMessage, MediaType contentType, Charset charset) throws IOException, HttpMessageConversionException {
         if (contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            OutputStreamWriter writer = new OutputStreamWriter(outputMessage.getBody(), charset);
            this.printer.appendTo(message, writer);
            writer.flush();
         } else {
            throw new HttpMessageConversionException("protobuf-java-util does not support printing " + contentType);
         }
      }
   }
}
