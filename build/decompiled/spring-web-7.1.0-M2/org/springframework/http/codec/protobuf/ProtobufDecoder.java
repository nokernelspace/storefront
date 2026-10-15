package org.springframework.http.codec.protobuf;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ProtobufDecoder extends ProtobufCodecSupport implements Decoder<Message> {
   protected static final int DEFAULT_MESSAGE_MAX_SIZE = 262144;
   private static final ConcurrentMap<Class<?>, Method> methodCache = new ConcurrentReferenceHashMap();
   private final ExtensionRegistry extensionRegistry;
   private int maxMessageSize = 262144;

   public ProtobufDecoder() {
      this(ExtensionRegistry.newInstance());
   }

   public ProtobufDecoder(ExtensionRegistry extensionRegistry) {
      Assert.notNull(extensionRegistry, "ExtensionRegistry must not be null");
      this.extensionRegistry = extensionRegistry;
   }

   public void setMaxMessageSize(int maxMessageSize) {
      this.maxMessageSize = maxMessageSize;
   }

   public int getMaxMessageSize() {
      return this.maxMessageSize;
   }

   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return Message.class.isAssignableFrom(elementType.toClass()) && this.supportsMimeType(mimeType);
   }

   public Flux<Message> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      ProtobufDecoder.MessageDecoderFunction decoderFunction = new ProtobufDecoder.MessageDecoderFunction(
         elementType, this.maxMessageSize, this.initMessageSizeReader()
      );
      return Flux.from(inputStream).flatMapIterable(decoderFunction).doOnTerminate(decoderFunction::discard);
   }

   protected ProtobufDecoder.MessageSizeReader initMessageSizeReader() {
      return new ProtobufDecoder.DefaultMessageSizeReader();
   }

   public Mono<Message> decodeToMono(
      Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      return DataBufferUtils.join(inputStream, this.maxMessageSize).map(dataBuffer -> this.decode(dataBuffer, elementType, mimeType, hints));
   }

   public Message decode(DataBuffer dataBuffer, ResolvableType targetType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) throws DecodingException {
      try {
         Builder builder = getMessageBuilder(targetType.toClass());
         this.merge(dataBuffer, builder);
         return builder.build();
      } catch (IOException ex) {
         throw new DecodingException("I/O error while parsing input stream", ex);
      } catch (Exception ex) {
         throw new DecodingException("Could not read Protobuf message: " + ex.getMessage(), ex);
      } finally {
         DataBufferUtils.release(dataBuffer);
      }
   }

   protected static Builder getMessageBuilder(Class<?> clazz) throws Exception {
      Method method = methodCache.get(clazz);
      if (method == null) {
         method = clazz.getMethod("newBuilder");
         methodCache.put(clazz, method);
      }

      return (Builder)method.invoke(clazz);
   }

   protected void merge(DataBuffer dataBuffer, Builder builder) throws IOException {
      ByteBuffer byteBuffer = ByteBuffer.allocate(dataBuffer.readableByteCount());
      dataBuffer.toByteBuffer(byteBuffer);
      builder.mergeFrom(CodedInputStream.newInstance(byteBuffer), this.extensionRegistry);
   }

   public List<MimeType> getDecodableMimeTypes() {
      return this.getMimeTypes();
   }

   private static class DefaultMessageSizeReader implements ProtobufDecoder.MessageSizeReader {
      private int offset;
      private int messageSize;

      @Override
      public @Nullable Integer readMessageSize(DataBuffer input) {
         if (this.offset == 0) {
            if (input.readableByteCount() == 0) {
               return null;
            }

            int firstByte = input.read();
            if ((firstByte & 128) == 0) {
               this.messageSize = firstByte;
               return this.getAndReset();
            }

            this.messageSize = firstByte & 127;
            this.offset = 7;
         }

         if (this.offset < 32) {
            while (this.offset < 32) {
               if (input.readableByteCount() == 0) {
                  return null;
               }

               int b = input.read();
               this.messageSize = this.messageSize | (b & 127) << this.offset;
               if ((b & 128) == 0) {
                  return this.getAndReset();
               }

               this.offset += 7;
            }
         }

         while (this.offset < 64) {
            if (input.readableByteCount() == 0) {
               return null;
            }

            int b = input.read();
            if ((b & 128) == 0) {
               return this.getAndReset();
            }

            this.offset += 7;
         }

         this.getAndReset();
         throw new DecodingException("Cannot parse message size: malformed varint");
      }

      private @Nullable Integer getAndReset() {
         Integer result = this.messageSize != 0 ? this.messageSize : null;
         this.offset = 0;
         this.messageSize = 0;
         return result;
      }
   }

   private class MessageDecoderFunction implements Function<DataBuffer, Iterable<? extends Message>> {
      private final ResolvableType elementType;
      private final int maxMessageSize;
      private @Nullable DataBuffer output;
      private int messageBytesToRead;
      private final ProtobufDecoder.MessageSizeReader messageSizeReader;

      public MessageDecoderFunction(ResolvableType elementType, int maxMessageSize, ProtobufDecoder.MessageSizeReader messageSizeReader) {
         this.elementType = elementType;
         this.maxMessageSize = maxMessageSize;
         this.messageSizeReader = messageSizeReader;
      }

      public Iterable<? extends Message> apply(DataBuffer input) {
         try {
            List<Message> messages = new ArrayList<>();

            int remainingBytesToRead;
            do {
               if (this.output == null) {
                  Integer messageSize = this.messageSizeReader.readMessageSize(input);
                  if (messageSize == null) {
                     return messages;
                  }

                  this.messageBytesToRead = messageSize;
                  if (this.maxMessageSize > 0 && this.messageBytesToRead > this.maxMessageSize) {
                     throw new DataBufferLimitException(
                        "The number of bytes to read for message (" + this.messageBytesToRead + ") exceeds the configured limit (" + this.maxMessageSize + ")"
                     );
                  }

                  this.output = input.factory().allocateBuffer(this.messageBytesToRead);
               }

               int chunkBytesToRead = Math.min(this.messageBytesToRead, input.readableByteCount());
               remainingBytesToRead = input.readableByteCount() - chunkBytesToRead;
               byte[] bytesToWrite = new byte[chunkBytesToRead];
               input.read(bytesToWrite, 0, chunkBytesToRead);
               this.output.write(bytesToWrite);
               this.messageBytesToRead -= chunkBytesToRead;
               if (this.messageBytesToRead == 0) {
                  ByteBuffer byteBuffer = ByteBuffer.allocate(this.output.readableByteCount());
                  this.output.toByteBuffer(byteBuffer);
                  CodedInputStream stream = CodedInputStream.newInstance(byteBuffer);
                  DataBufferUtils.release(this.output);
                  this.output = null;
                  Message message = ProtobufDecoder.getMessageBuilder(this.elementType.toClass())
                     .mergeFrom(stream, ProtobufDecoder.this.extensionRegistry)
                     .build();
                  messages.add(message);
               }
            } while (remainingBytesToRead > 0);

            return messages;
         } catch (DecodingException ex) {
            throw ex;
         } catch (IOException ex) {
            throw new DecodingException("I/O error while parsing input stream", ex);
         } catch (Exception ex) {
            throw new DecodingException("Could not read Protobuf message: " + ex.getMessage(), ex);
         } finally {
            DataBufferUtils.release(input);
         }
      }

      public void discard() {
         if (this.output != null) {
            DataBufferUtils.release(this.output);
         }
      }
   }

   protected interface MessageSizeReader {
      @Nullable Integer readMessageSize(DataBuffer input);
   }
}
