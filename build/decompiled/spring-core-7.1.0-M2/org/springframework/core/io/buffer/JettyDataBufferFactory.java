package org.springframework.core.io.buffer;

import java.nio.ByteBuffer;
import java.util.List;
import org.eclipse.jetty.io.Content.Chunk;

public class JettyDataBufferFactory implements DataBufferFactory {
   private final DefaultDataBufferFactory delegate;

   public JettyDataBufferFactory() {
      this(false);
   }

   public JettyDataBufferFactory(boolean preferDirect) {
      this(preferDirect, 256);
   }

   public JettyDataBufferFactory(boolean preferDirect, int defaultInitialCapacity) {
      this.delegate = new DefaultDataBufferFactory(preferDirect, defaultInitialCapacity);
   }

   @Deprecated(since = "6.0")
   @Override
   public DataBuffer allocateBuffer() {
      DefaultDataBuffer delegate = this.delegate.allocateBuffer();
      return new JettyVirtualDataBuffer(this, delegate);
   }

   @Override
   public DataBuffer allocateBuffer(int initialCapacity) {
      DefaultDataBuffer delegate = this.delegate.allocateBuffer(initialCapacity);
      return new JettyVirtualDataBuffer(this, delegate);
   }

   @Override
   public DataBuffer wrap(ByteBuffer byteBuffer) {
      DefaultDataBuffer delegate = this.delegate.wrap(byteBuffer);
      return new JettyVirtualDataBuffer(this, delegate);
   }

   @Override
   public DataBuffer wrap(byte[] bytes) {
      DefaultDataBuffer delegate = this.delegate.wrap(bytes);
      return new JettyVirtualDataBuffer(this, delegate);
   }

   public JettyDataBuffer wrap(Chunk chunk) {
      ByteBuffer byteBuffer = chunk.getByteBuffer();
      DefaultDataBuffer delegate = this.delegate.wrap(byteBuffer);
      return new JettyDataBuffer(this, delegate, chunk);
   }

   @Override
   public DataBuffer join(List<? extends DataBuffer> dataBuffers) {
      DefaultDataBuffer delegate = this.delegate.join(dataBuffers);
      return new JettyVirtualDataBuffer(this, delegate);
   }

   @Override
   public boolean isDirect() {
      return this.delegate.isDirect();
   }
}
