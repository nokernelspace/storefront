package org.springframework.core.io.buffer;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.function.IntPredicate;
import org.springframework.util.Assert;

public class DataBufferWrapper implements DataBuffer {
   private final DataBuffer delegate;

   public DataBufferWrapper(DataBuffer delegate) {
      Assert.notNull(delegate, "Delegate must not be null");
      this.delegate = delegate;
   }

   public DataBuffer dataBuffer() {
      return this.delegate;
   }

   @Override
   public DataBufferFactory factory() {
      return this.delegate.factory();
   }

   @Override
   public int indexOf(IntPredicate predicate, int fromIndex) {
      return this.delegate.indexOf(predicate, fromIndex);
   }

   @Override
   public int lastIndexOf(IntPredicate predicate, int fromIndex) {
      return this.delegate.lastIndexOf(predicate, fromIndex);
   }

   @Override
   public int readableByteCount() {
      return this.delegate.readableByteCount();
   }

   @Override
   public int writableByteCount() {
      return this.delegate.writableByteCount();
   }

   @Override
   public int capacity() {
      return this.delegate.capacity();
   }

   @Deprecated(since = "6.0")
   @Override
   public DataBuffer capacity(int capacity) {
      return this.delegate.capacity(capacity);
   }

   @Deprecated(since = "6.0")
   @Override
   public DataBuffer ensureCapacity(int capacity) {
      return this.delegate.ensureCapacity(capacity);
   }

   @Override
   public DataBuffer ensureWritable(int capacity) {
      return this.delegate.ensureWritable(capacity);
   }

   @Override
   public int readPosition() {
      return this.delegate.readPosition();
   }

   @Override
   public DataBuffer readPosition(int readPosition) {
      return this.delegate.readPosition(readPosition);
   }

   @Override
   public int writePosition() {
      return this.delegate.writePosition();
   }

   @Override
   public DataBuffer writePosition(int writePosition) {
      return this.delegate.writePosition(writePosition);
   }

   @Override
   public byte getByte(int index) {
      return this.delegate.getByte(index);
   }

   @Override
   public byte read() {
      return this.delegate.read();
   }

   @Override
   public DataBuffer read(byte[] destination) {
      return this.delegate.read(destination);
   }

   @Override
   public DataBuffer read(byte[] destination, int offset, int length) {
      return this.delegate.read(destination, offset, length);
   }

   @Override
   public DataBuffer write(byte b) {
      return this.delegate.write(b);
   }

   @Override
   public DataBuffer write(byte[] source) {
      return this.delegate.write(source);
   }

   @Override
   public DataBuffer write(byte[] source, int offset, int length) {
      return this.delegate.write(source, offset, length);
   }

   @Override
   public DataBuffer write(DataBuffer... buffers) {
      return this.delegate.write(buffers);
   }

   @Override
   public DataBuffer write(ByteBuffer... buffers) {
      return this.delegate.write(buffers);
   }

   @Override
   public DataBuffer write(CharSequence charSequence, Charset charset) {
      return this.delegate.write(charSequence, charset);
   }

   @Deprecated(since = "6.0")
   @Override
   public DataBuffer slice(int index, int length) {
      return this.delegate.slice(index, length);
   }

   @Deprecated(since = "6.0")
   @Override
   public DataBuffer retainedSlice(int index, int length) {
      return this.delegate.retainedSlice(index, length);
   }

   @Override
   public DataBuffer split(int index) {
      return this.delegate.split(index);
   }

   @Deprecated(since = "6.0")
   @Override
   public ByteBuffer asByteBuffer() {
      return this.delegate.asByteBuffer();
   }

   @Deprecated(since = "6.0")
   @Override
   public ByteBuffer asByteBuffer(int index, int length) {
      return this.delegate.asByteBuffer(index, length);
   }

   @Deprecated(since = "6.0.5")
   @Override
   public ByteBuffer toByteBuffer() {
      return this.delegate.toByteBuffer();
   }

   @Deprecated(since = "6.0.5")
   @Override
   public ByteBuffer toByteBuffer(int index, int length) {
      return this.delegate.toByteBuffer(index, length);
   }

   @Override
   public void toByteBuffer(ByteBuffer dest) {
      this.delegate.toByteBuffer(dest);
   }

   @Override
   public void toByteBuffer(int srcPos, ByteBuffer dest, int destPos, int length) {
      this.delegate.toByteBuffer(srcPos, dest, destPos, length);
   }

   @Override
   public DataBuffer.ByteBufferIterator readableByteBuffers() {
      return this.delegate.readableByteBuffers();
   }

   @Override
   public DataBuffer.ByteBufferIterator writableByteBuffers() {
      return this.delegate.writableByteBuffers();
   }

   @Override
   public InputStream asInputStream() {
      return this.delegate.asInputStream();
   }

   @Override
   public InputStream asInputStream(boolean releaseOnClose) {
      return this.delegate.asInputStream(releaseOnClose);
   }

   @Override
   public OutputStream asOutputStream() {
      return this.delegate.asOutputStream();
   }

   @Override
   public String toString(Charset charset) {
      return this.delegate.toString(charset);
   }

   @Override
   public String toString(int index, int length, Charset charset) {
      return this.delegate.toString(index, length, charset);
   }
}
