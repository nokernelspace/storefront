package org.springframework.core.io.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import org.springframework.util.Assert;

final class DataBufferInputStream extends InputStream {
   private final DataBuffer dataBuffer;
   private final int end;
   private final boolean releaseOnClose;
   private boolean closed;
   private int mark;

   public DataBufferInputStream(DataBuffer dataBuffer, boolean releaseOnClose) {
      Assert.notNull(dataBuffer, "DataBuffer must not be null");
      this.dataBuffer = dataBuffer;
      int start = this.dataBuffer.readPosition();
      this.end = start + this.dataBuffer.readableByteCount();
      this.mark = start;
      this.releaseOnClose = releaseOnClose;
   }

   @Override
   public int read() throws IOException {
      this.checkClosed();
      return this.available() == 0 ? -1 : this.dataBuffer.read() & 0xFF;
   }

   @Override
   public int read(byte[] b, int off, int len) throws IOException {
      this.checkClosed();
      int available = this.available();
      if (available == 0) {
         return -1;
      }

      len = Math.min(available, len);
      this.dataBuffer.read(b, off, len);
      return len;
   }

   @Override
   public boolean markSupported() {
      return true;
   }

   @Override
   public void mark(int readLimit) {
      Assert.isTrue(readLimit > 0, "readLimit must be greater than 0");
      this.mark = this.dataBuffer.readPosition();
   }

   @Override
   public int available() {
      return Math.max(0, this.end - this.dataBuffer.readPosition());
   }

   @Override
   public void reset() {
      this.dataBuffer.readPosition(this.mark);
   }

   @Override
   public void close() {
      if (!this.closed) {
         if (this.releaseOnClose) {
            DataBufferUtils.release(this.dataBuffer);
         }

         this.closed = true;
      }
   }

   @Override
   public byte[] readNBytes(int len) throws IOException {
      if (len < 0) {
         throw new IllegalArgumentException("len < 0");
      }

      this.checkClosed();
      int size = Math.min(this.available(), len);
      byte[] out = new byte[size];
      this.dataBuffer.read(out);
      return out;
   }

   @Override
   public long skip(long n) throws IOException {
      this.checkClosed();
      if (n <= 0L) {
         return 0L;
      }

      int skipped = Math.min(this.available(), n > 2147483647L ? Integer.MAX_VALUE : (int)n);
      this.dataBuffer.readPosition(this.dataBuffer.readPosition() + skipped);
      return skipped;
   }

   @Override
   public long transferTo(OutputStream out) throws IOException {
      Objects.requireNonNull(out, "out");
      this.checkClosed();
      if (this.available() == 0) {
         return 0L;
      }

      byte[] buf = this.readAllBytes();
      out.write(buf);
      return buf.length;
   }

   private void checkClosed() throws IOException {
      if (this.closed) {
         throw new IOException("DataBufferInputStream is closed");
      }
   }
}
