package org.springframework.core.io.buffer;

public interface CloseableDataBuffer extends DataBuffer, AutoCloseable {
   @Override
   void close();
}
