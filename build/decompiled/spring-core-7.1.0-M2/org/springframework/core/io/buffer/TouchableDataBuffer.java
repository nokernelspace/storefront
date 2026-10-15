package org.springframework.core.io.buffer;

public interface TouchableDataBuffer extends DataBuffer {
   TouchableDataBuffer touch(Object hint);
}
