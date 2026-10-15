package org.springframework.core.io.buffer;

public interface PooledDataBuffer extends TouchableDataBuffer {
   boolean isAllocated();

   PooledDataBuffer retain();

   PooledDataBuffer touch(Object hint);

   boolean release();
}
