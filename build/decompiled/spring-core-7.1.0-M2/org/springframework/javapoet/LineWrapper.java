package org.springframework.javapoet;

import java.io.IOException;

final class LineWrapper {
   private final LineWrapper.RecordingAppendable out;
   private final String indent;
   private final int columnLimit;
   private boolean closed;
   private final StringBuilder buffer = new StringBuilder();
   private int column = 0;
   private int indentLevel = -1;
   private LineWrapper.FlushType nextFlush;

   LineWrapper(Appendable out, String indent, int columnLimit) {
      Util.checkNotNull(out, "out == null");
      this.out = new LineWrapper.RecordingAppendable(out);
      this.indent = indent;
      this.columnLimit = columnLimit;
   }

   char lastChar() {
      return this.out.lastChar;
   }

   void append(String s) throws IOException {
      if (this.closed) {
         throw new IllegalStateException("closed");
      }

      if (this.nextFlush != null) {
         int nextNewline = s.indexOf(10);
         if (nextNewline == -1 && this.column + s.length() <= this.columnLimit) {
            this.buffer.append(s);
            this.column = this.column + s.length();
            return;
         }

         boolean wrap = nextNewline == -1 || this.column + nextNewline > this.columnLimit;
         this.flush(wrap ? LineWrapper.FlushType.WRAP : this.nextFlush);
      }

      this.out.append(s);
      int lastNewline = s.lastIndexOf(10);
      this.column = lastNewline != -1 ? s.length() - lastNewline - 1 : this.column + s.length();
   }

   void wrappingSpace(int indentLevel) throws IOException {
      if (this.closed) {
         throw new IllegalStateException("closed");
      }

      if (this.nextFlush != null) {
         this.flush(this.nextFlush);
      }

      this.column++;
      this.nextFlush = LineWrapper.FlushType.SPACE;
      this.indentLevel = indentLevel;
   }

   void zeroWidthSpace(int indentLevel) throws IOException {
      if (this.closed) {
         throw new IllegalStateException("closed");
      }

      if (this.column != 0) {
         if (this.nextFlush != null) {
            this.flush(this.nextFlush);
         }

         this.nextFlush = LineWrapper.FlushType.EMPTY;
         this.indentLevel = indentLevel;
      }
   }

   void close() throws IOException {
      if (this.nextFlush != null) {
         this.flush(this.nextFlush);
      }

      this.closed = true;
   }

   private void flush(LineWrapper.FlushType flushType) throws IOException {
      switch (flushType) {
         case WRAP:
            this.out.append('\n');

            for (int i = 0; i < this.indentLevel; i++) {
               this.out.append(this.indent);
            }

            this.column = this.indentLevel * this.indent.length();
            this.column = this.column + this.buffer.length();
            break;
         case SPACE:
            this.out.append(' ');
         case EMPTY:
            break;
         default:
            throw new IllegalArgumentException("Unknown FlushType: " + flushType);
      }

      this.out.append(this.buffer);
      this.buffer.delete(0, this.buffer.length());
      this.indentLevel = -1;
      this.nextFlush = null;
   }

   private enum FlushType {
      WRAP,
      SPACE,
      EMPTY;
   }

   static final class RecordingAppendable implements Appendable {
      private final Appendable delegate;
      private char lastChar = 0;

      RecordingAppendable(Appendable delegate) {
         this.delegate = delegate;
      }

      @Override
      public Appendable append(CharSequence csq) throws IOException {
         int length = csq.length();
         if (length != 0) {
            this.lastChar = csq.charAt(length - 1);
         }

         return this.delegate.append(csq);
      }

      @Override
      public Appendable append(CharSequence csq, int start, int end) throws IOException {
         CharSequence sub = csq.subSequence(start, end);
         return this.append(sub);
      }

      @Override
      public Appendable append(char c) throws IOException {
         this.lastChar = c;
         return this.delegate.append(c);
      }
   }
}
