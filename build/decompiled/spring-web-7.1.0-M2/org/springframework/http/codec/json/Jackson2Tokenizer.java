package org.springframework.http.codec.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.async.ByteBufferFeeder;
import com.fasterxml.jackson.core.async.NonBlockingInputFeeder;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DataBuffer.ByteBufferIterator;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

@Deprecated(since = "7.0", forRemoval = true)
final class Jackson2Tokenizer {
   private final JsonParser parser;
   private final DeserializationContext deserializationContext;
   private final NonBlockingInputFeeder inputFeeder;
   private final boolean tokenizeArrayElements;
   private final boolean forceUseOfBigDecimal;
   private final int maxInMemorySize;
   private int objectDepth;
   private int arrayDepth;
   private int byteCount;
   private TokenBuffer tokenBuffer;

   private Jackson2Tokenizer(
      JsonParser parser, DeserializationContext deserializationContext, boolean tokenizeArrayElements, boolean forceUseOfBigDecimal, int maxInMemorySize
   ) {
      this.parser = parser;
      this.deserializationContext = deserializationContext;
      this.inputFeeder = this.parser.getNonBlockingInputFeeder();
      this.tokenizeArrayElements = tokenizeArrayElements;
      this.forceUseOfBigDecimal = forceUseOfBigDecimal;
      this.maxInMemorySize = maxInMemorySize;
      this.tokenBuffer = this.createToken();
   }

   private List<TokenBuffer> tokenize(DataBuffer dataBuffer) {
      try {
         int bufferSize = dataBuffer.readableByteCount();
         List<TokenBuffer> tokens = new ArrayList<>();
         if (this.inputFeeder instanceof ByteBufferFeeder byteBufferFeeder) {
            ByteBufferIterator iterator = dataBuffer.readableByteBuffers();

            try {
               while (iterator.hasNext()) {
                  byteBufferFeeder.feedInput((ByteBuffer)iterator.next());
                  this.parseTokens(tokens);
               }
            } catch (Throwable var16) {
               if (iterator != null) {
                  try {
                     iterator.close();
                  } catch (Throwable var15) {
                     var16.addSuppressed(var15);
                  }
               }

               throw var16;
            }

            if (iterator != null) {
               iterator.close();
            }
         } else if (this.inputFeeder instanceof ByteArrayFeeder byteArrayFeeder) {
            byte[] bytes = new byte[bufferSize];
            dataBuffer.read(bytes);
            byteArrayFeeder.feedInput(bytes, 0, bufferSize);
            this.parseTokens(tokens);
         }

         this.assertInMemorySize(bufferSize, tokens);
         return tokens;
      } catch (JsonProcessingException ex) {
         throw new DecodingException("JSON decoding error: " + ex.getOriginalMessage(), ex);
      } catch (IOException ex) {
         throw Exceptions.propagate(ex);
      } finally {
         DataBufferUtils.release(dataBuffer);
      }
   }

   private Flux<TokenBuffer> endOfInput() {
      return Flux.defer(() -> {
         this.inputFeeder.endOfInput();

         try {
            List<TokenBuffer> tokens = new ArrayList<>();
            this.parseTokens(tokens);
            return Flux.fromIterable(tokens);
         } catch (JsonProcessingException ex) {
            throw new DecodingException("JSON decoding error: " + ex.getOriginalMessage(), ex);
         } catch (IOException ex) {
            throw Exceptions.propagate(ex);
         }
      });
   }

   private void parseTokens(List<TokenBuffer> tokens) throws IOException {
      boolean previousNull = false;

      while (!this.parser.isClosed()) {
         JsonToken token = this.parser.nextToken();
         if (token == JsonToken.NOT_AVAILABLE || token == null && previousNull) {
            break;
         }

         if (token == null) {
            previousNull = true;
         } else {
            previousNull = false;
            this.updateDepth(token);
            if (!this.tokenizeArrayElements) {
               this.processTokenNormal(token, tokens);
            } else {
               this.processTokenArray(token, tokens);
            }
         }
      }
   }

   private void updateDepth(JsonToken token) {
      switch (token) {
         case START_OBJECT:
            this.objectDepth++;
            break;
         case END_OBJECT:
            this.objectDepth--;
            break;
         case START_ARRAY:
            this.arrayDepth++;
            break;
         case END_ARRAY:
            this.arrayDepth--;
      }
   }

   private void processTokenNormal(JsonToken token, List<TokenBuffer> result) throws IOException {
      this.tokenBuffer.copyCurrentEvent(this.parser);
      if ((token.isStructEnd() || token.isScalarValue()) && this.objectDepth == 0 && this.arrayDepth == 0) {
         result.add(this.tokenBuffer);
         this.tokenBuffer = this.createToken();
      }
   }

   private void processTokenArray(JsonToken token, List<TokenBuffer> result) throws IOException {
      if (!this.isTopLevelArrayToken(token)) {
         this.tokenBuffer.copyCurrentEvent(this.parser);
      }

      if (this.objectDepth == 0 && (this.arrayDepth == 0 || this.arrayDepth == 1) && (token == JsonToken.END_OBJECT || token.isScalarValue())) {
         result.add(this.tokenBuffer);
         this.tokenBuffer = this.createToken();
      }
   }

   private TokenBuffer createToken() {
      TokenBuffer tokenBuffer = new TokenBuffer(this.parser, this.deserializationContext);
      tokenBuffer.forceUseOfBigDecimal(this.forceUseOfBigDecimal);
      return tokenBuffer;
   }

   private boolean isTopLevelArrayToken(JsonToken token) {
      return this.objectDepth == 0 && (token == JsonToken.START_ARRAY && this.arrayDepth == 1 || token == JsonToken.END_ARRAY && this.arrayDepth == 0);
   }

   private void assertInMemorySize(int currentBufferSize, List<TokenBuffer> result) {
      if (this.maxInMemorySize >= 0) {
         if (!result.isEmpty()) {
            this.byteCount = 0;
         } else if (currentBufferSize > Integer.MAX_VALUE - this.byteCount) {
            this.raiseLimitException();
         } else {
            this.byteCount += currentBufferSize;
            if (this.byteCount > this.maxInMemorySize) {
               this.raiseLimitException();
            }
         }
      }
   }

   private void raiseLimitException() {
      throw new DataBufferLimitException("Exceeded limit on max bytes per JSON object: " + this.maxInMemorySize);
   }

   public static Flux<TokenBuffer> tokenize(
      Flux<DataBuffer> dataBuffers,
      JsonFactory jsonFactory,
      ObjectMapper objectMapper,
      boolean tokenizeArrays,
      boolean forceUseOfBigDecimal,
      int maxInMemorySize
   ) {
      try {
         JsonParser parser;
         if (jsonFactory.getFormatName().equals("Smile")) {
            parser = jsonFactory.createNonBlockingByteArrayParser();
         } else {
            parser = jsonFactory.createNonBlockingByteBufferParser();
         }

         DeserializationContext context = objectMapper.getDeserializationContext();
         if (context instanceof DefaultDeserializationContext ddc) {
            context = ddc.createInstance(objectMapper.getDeserializationConfig(), parser, objectMapper.getInjectableValues());
         }

         Jackson2Tokenizer tokenizer = new Jackson2Tokenizer(parser, context, tokenizeArrays, forceUseOfBigDecimal, maxInMemorySize);
         return dataBuffers.concatMapIterable(tokenizer::tokenize).concatWith(tokenizer.endOfInput());
      } catch (IOException ex) {
         return Flux.error(ex);
      }
   }
}
