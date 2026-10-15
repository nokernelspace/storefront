package org.springframework.core.io.buffer;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public class DefaultDataBuffer implements DataBuffer {
   private static final int MAX_CAPACITY = Integer.MAX_VALUE;
   private static final int CAPACITY_THRESHOLD = 4194304;
   private final DefaultDataBufferFactory dataBufferFactory;
   private ByteBuffer byteBuffer;
   private int capacity;
   private int readPosition;
   private int writePosition;

   private DefaultDataBuffer(DefaultDataBufferFactory dataBufferFactory, ByteBuffer byteBuffer) {
      Assert.notNull(dataBufferFactory, "DefaultDataBufferFactory must not be null");
      Assert.notNull(byteBuffer, "ByteBuffer must not be null");
      this.dataBufferFactory = dataBufferFactory;
      ByteBuffer slice = byteBuffer.slice();
      this.byteBuffer = slice;
      this.capacity = slice.remaining();
   }

   static DefaultDataBuffer fromFilledByteBuffer(DefaultDataBufferFactory dataBufferFactory, ByteBuffer byteBuffer) {
      DefaultDataBuffer dataBuffer = new DefaultDataBuffer(dataBufferFactory, byteBuffer);
      dataBuffer.writePosition(byteBuffer.remaining());
      return dataBuffer;
   }

   static DefaultDataBuffer fromEmptyByteBuffer(DefaultDataBufferFactory dataBufferFactory, ByteBuffer byteBuffer) {
      return new DefaultDataBuffer(dataBufferFactory, byteBuffer);
   }

   public ByteBuffer getNativeBuffer() {
      return this.byteBuffer.duplicate().position(this.readPosition).limit(this.writePosition);
   }

   private void setNativeBuffer(ByteBuffer byteBuffer) {
      this.byteBuffer = byteBuffer;
      this.capacity = byteBuffer.capacity();
   }

   public DefaultDataBufferFactory factory() {
      return this.dataBufferFactory;
   }

   @Override
   public int indexOf(IntPredicate predicate, int fromIndex) {
      Assert.notNull(predicate, "IntPredicate must not be null");
      if (fromIndex < 0) {
         fromIndex = 0;
      } else if (fromIndex >= this.writePosition) {
         return -1;
      }

      for (int i = fromIndex; i < this.writePosition; i++) {
         byte b = this.byteBuffer.get(i);
         if (predicate.test(b)) {
            return i;
         }
      }

      return -1;
   }

   @Override
   public int lastIndexOf(IntPredicate predicate, int fromIndex) {
      Assert.notNull(predicate, "IntPredicate must not be null");

      for (int i = Math.min(fromIndex, this.writePosition - 1); i >= 0; i--) {
         byte b = this.byteBuffer.get(i);
         if (predicate.test(b)) {
            return i;
         }
      }

      return -1;
   }

   @Override
   public int readableByteCount() {
      return this.writePosition - this.readPosition;
   }

   @Override
   public int writableByteCount() {
      return this.capacity - this.writePosition;
   }

   @Override
   public int readPosition() {
      return this.readPosition;
   }

   public DefaultDataBuffer readPosition(int readPosition) {
      this.assertIndex(readPosition >= 0, "'readPosition' %d must be >= 0", readPosition);
      this.assertIndex(readPosition <= this.writePosition, "'readPosition' %d must be <= %d", readPosition, this.writePosition);
      this.readPosition = readPosition;
      return this;
   }

   @Override
   public int writePosition() {
      return this.writePosition;
   }

   public DefaultDataBuffer writePosition(int writePosition) {
      this.assertIndex(writePosition >= this.readPosition, "'writePosition' %d must be >= %d", writePosition, this.readPosition);
      this.assertIndex(writePosition <= this.capacity, "'writePosition' %d must be <= %d", writePosition, this.capacity);
      this.writePosition = writePosition;
      return this;
   }

   @Override
   public int capacity() {
      return this.capacity;
   }

   @Deprecated(since = "6.0")
   @Override
   public DataBuffer capacity(int capacity) {
      this.setCapacity(capacity);
      return this;
   }

   private void setCapacity(int newCapacity) {
      if (newCapacity < 0) {
         throw new IllegalArgumentException(String.format("'newCapacity' %d must be 0 or higher", newCapacity));
      }

      int readPosition = this.readPosition();
      int writePosition = this.writePosition();
      int oldCapacity = this.capacity();
      if (newCapacity > oldCapacity) {
         ByteBuffer oldBuffer = this.byteBuffer;
         ByteBuffer newBuffer = allocate(newCapacity, oldBuffer.isDirect());
         oldBuffer.position(0).limit(oldBuffer.capacity());
         newBuffer.position(0).limit(oldBuffer.capacity());
         newBuffer.put(oldBuffer);
         newBuffer.clear();
         this.setNativeBuffer(newBuffer);
      } else if (newCapacity < oldCapacity) {
         ByteBuffer oldBuffer = this.byteBuffer;
         ByteBuffer newBuffer = allocate(newCapacity, oldBuffer.isDirect());
         if (readPosition < newCapacity) {
            if (writePosition > newCapacity) {
               writePosition = newCapacity;
               this.writePosition(writePosition);
            }

            oldBuffer.position(readPosition).limit(writePosition);
            newBuffer.position(readPosition).limit(writePosition);
            newBuffer.put(oldBuffer);
            newBuffer.clear();
         } else {
            this.readPosition(newCapacity);
            this.writePosition(newCapacity);
         }

         this.setNativeBuffer(newBuffer);
      }
   }

   @Override
   public DataBuffer ensureWritable(int length) {
      if (length > this.writableByteCount()) {
         int newCapacity = this.calculateCapacity(this.writePosition + length);
         this.setCapacity(newCapacity);
      }

      return this;
   }

   private static ByteBuffer allocate(int capacity, boolean direct) {
      return direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
   }

   @Override
   public byte getByte(int index) {
      this.assertIndex(index >= 0, "index %d must be >= 0", index);
      this.assertIndex(index <= this.writePosition - 1, "index %d must be <= %d", index, this.writePosition - 1);
      return this.byteBuffer.get(index);
   }

   @Override
   public byte read() {
      this.assertIndex(this.readPosition <= this.writePosition - 1, "readPosition %d must be <= %d", this.readPosition, this.writePosition - 1);
      int pos = this.readPosition;
      byte b = this.byteBuffer.get(pos);
      this.readPosition = pos + 1;
      return b;
   }

   public DefaultDataBuffer read(byte[] destination) {
      Assert.notNull(destination, "Byte array must not be null");
      this.read(destination, 0, destination.length);
      return this;
   }

   public DefaultDataBuffer read(byte[] destination, int offset, int length) {
      Assert.notNull(destination, "Byte array must not be null");
      this.assertIndex(
         this.readPosition <= this.writePosition - length,
         "readPosition %d and length %d should be smaller than writePosition %d",
         this.readPosition,
         length,
         this.writePosition
      );
      ByteBuffer tmp = this.byteBuffer.duplicate();
      int limit = this.readPosition + length;
      tmp.clear().position(this.readPosition).limit(limit);
      tmp.get(destination, offset, length);
      this.readPosition += length;
      return this;
   }

   public DefaultDataBuffer write(byte b) {
      this.ensureWritable(1);
      int pos = this.writePosition;
      this.byteBuffer.put(pos, b);
      this.writePosition = pos + 1;
      return this;
   }

   public DefaultDataBuffer write(byte[] source) {
      Assert.notNull(source, "Byte array must not be null");
      this.write(source, 0, source.length);
      return this;
   }

   public DefaultDataBuffer write(byte[] source, int offset, int length) {
      Assert.notNull(source, "Byte array must not be null");
      this.ensureWritable(length);
      ByteBuffer tmp = this.byteBuffer.duplicate();
      int limit = this.writePosition + length;
      tmp.clear().position(this.writePosition).limit(limit);
      tmp.put(source, offset, length);
      this.writePosition += length;
      return this;
   }

   public DefaultDataBuffer write(DataBuffer... dataBuffers) {
      if (!ObjectUtils.isEmpty(dataBuffers)) {
         ByteBuffer[] byteBuffers = new ByteBuffer[dataBuffers.length];

         for (int i = 0; i < dataBuffers.length; i++) {
            byteBuffers[i] = ByteBuffer.allocate(dataBuffers[i].readableByteCount());
            dataBuffers[i].toByteBuffer(byteBuffers[i]);
         }

         this.write(byteBuffers);
      }

      return this;
   }

   public DefaultDataBuffer write(ByteBuffer... buffers) {
      if (!ObjectUtils.isEmpty(buffers)) {
         int capacity = Arrays.stream(buffers).mapToInt(Buffer::remaining).sum();
         this.ensureWritable(capacity);
         Arrays.stream(buffers).forEach(this::write);
      }

      return this;
   }

   private void write(ByteBuffer source) {
      int length = source.remaining();
      ByteBuffer tmp = this.byteBuffer.duplicate();
      int limit = this.writePosition + source.remaining();
      tmp.clear().position(this.writePosition).limit(limit);
      tmp.put(source);
      this.writePosition += length;
   }

   @Deprecated(since = "6.0")
   public DefaultDataBuffer slice(int index, int length) {
      this.checkIndex(index, length);
      int oldPosition = this.byteBuffer.position();

      try {
         this.byteBuffer.position(index);
         ByteBuffer slice = this.byteBuffer.slice();
         slice.limit(length);
         return new DefaultDataBuffer.SlicedDefaultDataBuffer(slice, this.dataBufferFactory, length);
      } finally {
         this.byteBuffer.position(oldPosition);
      }
   }

   public DefaultDataBuffer split(int index) {
      this.checkIndex(index);
      ByteBuffer split = this.byteBuffer.duplicate().clear().position(0).limit(index).slice();
      DefaultDataBuffer result = new DefaultDataBuffer(this.dataBufferFactory, split);
      result.writePosition = Math.min(this.writePosition, index);
      result.readPosition = Math.min(this.readPosition, index);
      this.byteBuffer = this.byteBuffer.duplicate().clear().position(index).limit(this.byteBuffer.capacity()).slice();
      this.writePosition = Math.max(this.writePosition, index) - index;
      this.readPosition = Math.max(this.readPosition, index) - index;
      this.capacity = this.byteBuffer.capacity();
      return result;
   }

   @Deprecated(since = "6.0")
   @Override
   public ByteBuffer asByteBuffer() {
      return this.asByteBuffer(this.readPosition, this.readableByteCount());
   }

   @Deprecated(since = "6.0")
   @Override
   public ByteBuffer asByteBuffer(int index, int length) {
      this.checkIndex(index, length);
      ByteBuffer duplicate = this.byteBuffer.duplicate();
      duplicate.position(index);
      duplicate.limit(index + length);
      return duplicate.slice();
   }

   @Deprecated(since = "6.0.5")
   @Override
   public ByteBuffer toByteBuffer(int index, int length) {
      this.checkIndex(index, length);
      ByteBuffer copy = allocate(length, this.byteBuffer.isDirect());
      ByteBuffer readOnly = this.byteBuffer.asReadOnlyBuffer();
      readOnly.clear().position(index).limit(index + length);
      copy.put(readOnly);
      return copy.flip();
   }

   @Override
   public void toByteBuffer(int srcPos, ByteBuffer dest, int destPos, int length) {
      this.checkIndex(srcPos, length);
      Assert.notNull(dest, "Dest must not be null");
      dest = dest.duplicate().clear();
      dest.put(destPos, this.byteBuffer, srcPos, length);
   }

   @Override
   public DataBuffer.ByteBufferIterator readableByteBuffers() {
      ByteBuffer readOnly = this.byteBuffer.slice(this.readPosition, this.readableByteCount()).asReadOnlyBuffer();
      return new DefaultDataBuffer.ByteBufferIterator(readOnly);
   }

   @Override
   public DataBuffer.ByteBufferIterator writableByteBuffers() {
      ByteBuffer slice = this.byteBuffer.slice(this.writePosition, this.writableByteCount());
      return new DefaultDataBuffer.ByteBufferIterator(slice);
   }

   @Override
   public String toString(int index, int length, Charset charset) {
      this.checkIndex(index, length);
      Assert.notNull(charset, "Charset must not be null");
      byte[] bytes;
      int offset;
      if (this.byteBuffer.hasArray()) {
         bytes = this.byteBuffer.array();
         offset = this.byteBuffer.arrayOffset() + index;
      } else {
         bytes = new byte[length];
         offset = 0;
         ByteBuffer duplicate = this.byteBuffer.duplicate();
         duplicate.clear().position(index).limit(index + length);
         duplicate.get(bytes, 0, length);
      }

      return new String(bytes, offset, length, charset);
   }

   private int calculateCapacity(int neededCapacity) {
      Assert.isTrue(neededCapacity >= 0, "'neededCapacity' must be >= 0");
      if (neededCapacity == 4194304) {
         return 4194304;
      }

      if (neededCapacity > 4194304) {
         int newCapacity = neededCapacity / 4194304 * 4194304;
         if (newCapacity > 2143289343) {
            newCapacity = Integer.MAX_VALUE;
         } else {
            newCapacity += 4194304;
         }

         return newCapacity;
      } else {
         int newCapacity = 64;

         while (newCapacity < neededCapacity) {
            newCapacity <<= 1;
         }

         return Math.min(newCapacity, Integer.MAX_VALUE);
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof DefaultDataBuffer that
            && this.readPosition == that.readPosition
            && this.writePosition == that.writePosition
            && this.byteBuffer.equals(that.byteBuffer);
   }

   @Override
   public int hashCode() {
      return this.byteBuffer.hashCode();
   }

   @Override
   public String toString() {
      return String.format("DefaultDataBuffer (r: %d, w: %d, c: %d)", this.readPosition, this.writePosition, this.capacity);
   }

   private void checkIndex(int index, int length) {
      this.checkIndex(index);
      this.checkLength(length);
   }

   private void checkIndex(int index) {
      this.assertIndex(index >= 0, "index %d must be >= 0", index);
      this.assertIndex(index <= this.capacity, "index %d must be <= %d", index, this.capacity);
   }

   private void checkLength(int length) {
      this.assertIndex(length >= 0, "length %d must be >= 0", length);
      this.assertIndex(length <= this.capacity, "length %d must be <= %d", length, this.capacity);
   }

   private void assertIndex(boolean expression, String format, Object... args) {
      if (!expression) {
         String message = String.format(format, args);
         throw new IndexOutOfBoundsException(message);
      }
   }

   private static final class ByteBufferIterator implements DataBuffer.ByteBufferIterator {
      private final ByteBuffer buffer;
      private boolean hasNext = true;

      public ByteBufferIterator(ByteBuffer buffer) {
         this.buffer = buffer;
      }

      @Override
      public boolean hasNext() {
         return this.hasNext;
      }

      public ByteBuffer next() {
         if (!this.hasNext) {
            throw new NoSuchElementException();
         }

         this.hasNext = false;
         return this.buffer;
      }

      @Override
      public void close() {
      }
   }

   private static class SlicedDefaultDataBuffer extends DefaultDataBuffer {
      SlicedDefaultDataBuffer(ByteBuffer byteBuffer, DefaultDataBufferFactory dataBufferFactory, int length) {
         super(dataBufferFactory, byteBuffer);
         this.writePosition(length);
      }

      public DefaultDataBuffer capacity(int newCapacity) {
         throw new UnsupportedOperationException("Changing the capacity of a sliced buffer is not supported");
      }
   }
}
