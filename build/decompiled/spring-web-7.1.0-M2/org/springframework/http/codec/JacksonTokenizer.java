package org.springframework.http.codec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DataBuffer.ByteBufferIterator;
import reactor.core.publisher.Flux;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.core.async.ByteBufferFeeder;
import tools.jackson.core.async.NonBlockingInputFeeder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.util.TokenBuffer;

final class JacksonTokenizer {
   private final JsonParser parser;
   private final NonBlockingInputFeeder inputFeeder;
   private final boolean tokenizeArrayElements;
   private final boolean forceUseOfBigDecimal;
   private final int maxInMemorySize;
   private int objectDepth;
   private int arrayDepth;
   private int byteCount;
   private TokenBuffer tokenBuffer;

   private JacksonTokenizer(JsonParser parser, boolean tokenizeArrayElements, boolean forceUseOfBigDecimal, int maxInMemorySize) {
      this.parser = parser;
      this.inputFeeder = this.parser.nonBlockingInputFeeder();
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
            } catch (Throwable var15) {
               if (iterator != null) {
                  try {
                     iterator.close();
                  } catch (Throwable var14) {
                     var15.addSuppressed(var14);
                  }
               }

               throw var15;
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
      } catch (JacksonException ex) {
         throw new DecodingException("JSON decoding error: " + ex.getOriginalMessage(), ex);
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
         } catch (JacksonException ex) {
            throw new DecodingException("JSON decoding error: " + ex.getOriginalMessage(), ex);
         }
      });
   }

   private void parseTokens(List<TokenBuffer> tokens) {
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

   private void processTokenNormal(JsonToken token, List<TokenBuffer> result) {
      this.tokenBuffer.copyCurrentEvent(this.parser);
      if ((token.isStructEnd() || token.isScalarValue()) && this.objectDepth == 0 && this.arrayDepth == 0) {
         result.add(this.tokenBuffer);
         this.tokenBuffer = this.createToken();
      }
   }

   private void processTokenArray(JsonToken token, List<TokenBuffer> result) {
      if (!this.isTopLevelArrayToken(token)) {
         this.tokenBuffer.copyCurrentEvent(this.parser);
      }

      if (this.objectDepth == 0 && (this.arrayDepth == 0 || this.arrayDepth == 1) && (token == JsonToken.END_OBJECT || token.isScalarValue())) {
         result.add(this.tokenBuffer);
         this.tokenBuffer = this.createToken();
      }
   }

   private TokenBuffer createToken() {
      TokenBuffer tokenBuffer = TokenBuffer.forBuffering(this.parser, this.parser.objectReadContext());
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
      Flux<DataBuffer> dataBuffers, ObjectMapper objectMapper, boolean tokenizeArrays, boolean forceUseOfBigDecimal, int maxInMemorySize
   ) {
      try {
         JsonParser parser;
         try {
            parser = objectMapper.createNonBlockingByteBufferParser();
         } catch (UnsupportedOperationException ex) {
            parser = objectMapper.createNonBlockingByteArrayParser();
         }

         JacksonTokenizer tokenizer = new JacksonTokenizer(parser, tokenizeArrays, forceUseOfBigDecimal, maxInMemorySize);
         return dataBuffers.concatMapIterable(tokenizer::tokenize).concatWith(tokenizer.endOfInput());
      } catch (JacksonException ex) {
         return Flux.error(ex);
      }
   }
}
