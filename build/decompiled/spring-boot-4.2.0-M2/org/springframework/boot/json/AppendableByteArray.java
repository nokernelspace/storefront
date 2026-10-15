package org.springframework.boot.json;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.function.ThrowingConsumer;

class AppendableByteArray implements Appendable {
   private static final ThreadLocal<@Nullable SoftReference<AppendableByteArray>> cache = new ThreadLocal<>();
   private static final int DEFAULT_INITIAL_SIZE = 8192;
   private static final int DEFAULT_EXPANSION_SIZE = 8192;
   private static final byte[] NO_BYTES = new byte[0];
   private final Charset charset;
   private final CharsetEncoder encoder;
   private final int expansionSize;
   private ByteBuffer out;
   private char highSurrogate;

   AppendableByteArray(Charset charset) {
      this(charset, 8192, 8192);
   }

   AppendableByteArray(Charset charset, int initialSize, int expansionSize) {
      this.charset = charset;
      this.encoder = charset.newEncoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
      this.out = ByteBuffer.allocate(initialSize);
      this.expansionSize = expansionSize;
   }

   public AppendableByteArray append(@Nullable CharSequence charSequence, int start, int end) throws IOException {
      return this.append((charSequence != null ? charSequence : "null").subSequence(start, end));
   }

   public AppendableByteArray append(CharSequence charSequence) throws IOException {
      return this.append(String.valueOf(charSequence).toCharArray());
   }

   public AppendableByteArray append(char ch) throws IOException {
      return this.append(new char[]{ch});
   }

   private AppendableByteArray append(char[] chars) throws IOException {
      return chars.length != 0 ? this.append(CharBuffer.wrap(chars)) : this;
   }

   private AppendableByteArray append(CharBuffer in) throws IOException {
      if (this.highSurrogate != 0) {
         CharBuffer pending = CharBuffer.allocate(in.remaining() + 1);
         pending.put(this.highSurrogate).put(in).flip();
         this.highSurrogate = 0;
         in = pending;
      }

      return this.append(in, false);
   }

   private AppendableByteArray append(CharBuffer in, boolean endOfInput) throws IOException {
      CoderResult result = this.encoder.encode(in, this.out, endOfInput);
      if (result.isUnderflow()) {
         this.highSurrogate = in.hasRemaining() ? in.get() : '\u0000';
         return this;
      } else if (result.isOverflow()) {
         ByteBuffer out = this.out;
         this.out = ByteBuffer.allocate(out.capacity() + this.expansionSize);
         out.flip();
         this.out.put(out);
         return this.append(in, endOfInput);
      } else {
         result.throwException();
         return this;
      }
   }

   byte[] toByteArray() throws IOException {
      if (this.highSurrogate != 0) {
         CharBuffer in = CharBuffer.wrap(new char[]{this.highSurrogate});
         this.highSurrogate = 0;
         this.append(in, true);
      }

      this.out.flip();
      int limit = this.out.limit();
      int position = this.out.position();
      int size = limit - position;
      if (size <= 0) {
         return NO_BYTES;
      }

      byte[] result = new byte[size];
      System.arraycopy(this.out.array(), this.out.arrayOffset() + position, result, 0, size);
      return result;
   }

   private void reset() {
      this.out.clear();
      this.encoder.reset();
      this.highSurrogate = 0;
   }

   static byte[] toByteArray(Charset charset, ThrowingConsumer<Appendable> appendable) throws IOException {
      Assert.notNull(charset, "'charset' must not be null");
      Assert.notNull(appendable, "'appendable' must not be null");
      AppendableByteArray appendableByteArray = get(charset);

      try {
         appendable.acceptWithException(appendableByteArray);
         return appendableByteArray.toByteArray();
      } catch (IOException | RuntimeException ex) {
         throw ex;
      } catch (Exception ex) {
         throw new IllegalStateException(ex);
      } finally {
         appendableByteArray.reset();
      }
   }

   private static AppendableByteArray get(Charset charset) {
      Assert.notNull(charset, "'charset' must not be null");
      SoftReference<AppendableByteArray> cached = cache.get();
      AppendableByteArray result = cached != null ? cached.get() : null;
      if (result == null || !result.charset.equals(charset)) {
         result = new AppendableByteArray(charset);
         cache.set(new SoftReference<>(result));
      }

      return result;
   }
}
