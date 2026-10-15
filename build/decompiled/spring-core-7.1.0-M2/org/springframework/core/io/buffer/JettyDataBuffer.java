package org.springframework.core.io.buffer;

import java.nio.ByteBuffer;
import org.eclipse.jetty.io.Content.Chunk;
import org.springframework.util.Assert;

public final class JettyDataBuffer extends JettyVirtualDataBuffer {
   private final Chunk chunk;

   JettyDataBuffer(JettyDataBufferFactory bufferFactory, DataBuffer delegate, Chunk chunk) {
      super(bufferFactory, delegate);
      Assert.notNull(chunk, "Chunk must not be null");
      this.chunk = chunk;
   }

   @Override
   public PooledDataBuffer retain() {
      int result = this.incrementRefCount();
      if (result != 0) {
         this.chunk.retain();
      }

      return this;
   }

   @Override
   public boolean release() {
      this.decrementRefCount();
      return this.chunk.release();
   }

   @Deprecated(since = "6.0")
   @Override
   public DataBuffer slice(int index, int length) {
      DataBuffer delegateSlice = super.slice(index, length);
      this.chunk.retain();
      return new JettyDataBuffer(this.bufferFactory, delegateSlice, this.chunk);
   }

   @Override
   public DataBuffer split(int index) {
      DataBuffer delegateSplit = super.split(index);
      this.chunk.retain();
      return new JettyDataBuffer(this.bufferFactory, delegateSplit, this.chunk);
   }

   @Override
   public DataBuffer.ByteBufferIterator readableByteBuffers() {
      DataBuffer.ByteBufferIterator delegateIterator = super.readableByteBuffers();
      return new JettyDataBuffer.JettyByteBufferIterator(delegateIterator, this.chunk);
   }

   @Override
   public DataBuffer.ByteBufferIterator writableByteBuffers() {
      DataBuffer.ByteBufferIterator delegateIterator = super.writableByteBuffers();
      return new JettyDataBuffer.JettyByteBufferIterator(delegateIterator, this.chunk);
   }

   private static final class JettyByteBufferIterator implements DataBuffer.ByteBufferIterator {
      private final DataBuffer.ByteBufferIterator delegate;
      private final Chunk chunk;

      public JettyByteBufferIterator(DataBuffer.ByteBufferIterator delegate, Chunk chunk) {
         Assert.notNull(delegate, "Delegate must not be null");
         Assert.notNull(chunk, "Chunk must not be null");
         this.delegate = delegate;
         this.chunk = chunk;
         this.chunk.retain();
      }

      @Override
      public void close() {
         this.delegate.close();
         this.chunk.release();
      }

      @Override
      public boolean hasNext() {
         return this.delegate.hasNext();
      }

      public ByteBuffer next() {
         return this.delegate.next();
      }
   }
}
