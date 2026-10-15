package org.springframework.core.io.buffer;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Iterator;
import java.util.function.IntPredicate;
import org.springframework.util.Assert;

public interface DataBuffer {
   DataBufferFactory factory();

   int indexOf(IntPredicate predicate, int fromIndex);

   int lastIndexOf(IntPredicate predicate, int fromIndex);

   int readableByteCount();

   int writableByteCount();

   int capacity();

   @Deprecated(since = "6.0")
   DataBuffer capacity(int capacity);

   @Deprecated(since = "6.0")
   default DataBuffer ensureCapacity(int capacity) {
      return this.ensureWritable(capacity);
   }

   DataBuffer ensureWritable(int capacity);

   int readPosition();

   DataBuffer readPosition(int readPosition);

   int writePosition();

   DataBuffer writePosition(int writePosition);

   byte getByte(int index);

   default int forEachByte(int index, int length, DataBuffer.ByteProcessor processor) {
      Assert.isTrue(length >= 0, "Length must be >= 0");

      for (int position = index; position < index + length; position++) {
         byte b = this.getByte(position);
         if (!processor.process(b)) {
            return position;
         }
      }

      return -1;
   }

   byte read();

   DataBuffer read(byte[] destination);

   DataBuffer read(byte[] destination, int offset, int length);

   DataBuffer write(byte b);

   DataBuffer write(byte[] source);

   DataBuffer write(byte[] source, int offset, int length);

   DataBuffer write(DataBuffer... buffers);

   DataBuffer write(ByteBuffer... buffers);

   default DataBuffer write(CharSequence charSequence, Charset charset) {
      Assert.notNull(charSequence, "CharSequence must not be null");
      Assert.notNull(charset, "Charset must not be null");
      if (!charSequence.isEmpty()) {
         CharsetEncoder encoder = charset.newEncoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
         CharBuffer src = CharBuffer.wrap(charSequence);
         int averageSize = (int)Math.ceil(src.remaining() * encoder.averageBytesPerChar());
         this.ensureWritable(averageSize);

         while (true) {
            CoderResult cr;
            if (src.hasRemaining()) {
               try (DataBuffer.ByteBufferIterator iterator = this.writableByteBuffers()) {
                  Assert.state(iterator.hasNext(), "No ByteBuffer available");
                  ByteBuffer dest = iterator.next();
                  cr = encoder.encode(src, dest, true);
                  if (cr.isUnderflow()) {
                     cr = encoder.flush(dest);
                  }

                  this.writePosition(this.writePosition() + dest.position());
               }
            } else {
               cr = CoderResult.UNDERFLOW;
            }

            if (cr.isUnderflow()) {
               break;
            }

            if (cr.isOverflow()) {
               int maxSize = (int)Math.ceil(src.remaining() * encoder.maxBytesPerChar());
               this.ensureWritable(maxSize);
            }
         }
      }

      return this;
   }

   @Deprecated(since = "6.0")
   DataBuffer slice(int index, int length);

   @Deprecated(since = "6.0")
   default DataBuffer retainedSlice(int index, int length) {
      return DataBufferUtils.retain(this.slice(index, length));
   }

   DataBuffer split(int index);

   @Deprecated(since = "6.0")
   ByteBuffer asByteBuffer();

   @Deprecated(since = "6.0")
   ByteBuffer asByteBuffer(int index, int length);

   @Deprecated(since = "6.0.5")
   default ByteBuffer toByteBuffer() {
      return this.toByteBuffer(this.readPosition(), this.readableByteCount());
   }

   @Deprecated(since = "6.0.5")
   ByteBuffer toByteBuffer(int index, int length);

   default void toByteBuffer(ByteBuffer dest) {
      this.toByteBuffer(this.readPosition(), dest, dest.position(), this.readableByteCount());
   }

   void toByteBuffer(int srcPos, ByteBuffer dest, int destPos, int length);

   DataBuffer.ByteBufferIterator readableByteBuffers();

   DataBuffer.ByteBufferIterator writableByteBuffers();

   default InputStream asInputStream() {
      return new DataBufferInputStream(this, false);
   }

   default InputStream asInputStream(boolean releaseOnClose) {
      return new DataBufferInputStream(this, releaseOnClose);
   }

   default OutputStream asOutputStream() {
      return new DataBufferOutputStream(this);
   }

   default String toString(Charset charset) {
      Assert.notNull(charset, "Charset must not be null");
      return this.toString(this.readPosition(), this.readableByteCount(), charset);
   }

   String toString(int index, int length, Charset charset);

   interface ByteBufferIterator extends Iterator<ByteBuffer>, Closeable {
      @Override
      void close();
   }

   @FunctionalInterface
   interface ByteProcessor {
      boolean process(byte b);
   }
}
