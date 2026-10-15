package org.springframework.core.io.buffer;

import java.io.IOException;
import java.io.OutputStream;
import org.springframework.util.Assert;

final class DataBufferOutputStream extends OutputStream {
   private final DataBuffer dataBuffer;
   private boolean closed;

   public DataBufferOutputStream(DataBuffer dataBuffer) {
      Assert.notNull(dataBuffer, "DataBuffer must not be null");
      this.dataBuffer = dataBuffer;
   }

   @Override
   public void write(int b) throws IOException {
      this.checkClosed();
      this.dataBuffer.ensureWritable(1);
      this.dataBuffer.write((byte)b);
   }

   @Override
   public void write(byte[] b, int off, int len) throws IOException {
      this.checkClosed();
      if (len > 0) {
         this.dataBuffer.ensureWritable(len);
         this.dataBuffer.write(b, off, len);
      }
   }

   @Override
   public void close() {
      if (!this.closed) {
         this.closed = true;
      }
   }

   private void checkClosed() throws IOException {
      if (this.closed) {
         throw new IOException("DataBufferOutputStream is closed");
      }
   }
}
