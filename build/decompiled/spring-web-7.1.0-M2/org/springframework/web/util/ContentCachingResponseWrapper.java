package org.springframework.web.util;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.util.FastByteArrayOutputStream;

public class ContentCachingResponseWrapper extends HttpServletResponseWrapper {
   private final FastByteArrayOutputStream content = new FastByteArrayOutputStream(1024);
   private @Nullable ServletOutputStream outputStream;
   private @Nullable PrintWriter writer;
   private @Nullable Integer contentLength;

   public ContentCachingResponseWrapper(HttpServletResponse response) {
      super(response);
   }

   public void sendError(int sc) throws IOException {
      this.copyBodyToResponse(false);

      try {
         super.sendError(sc);
      } catch (IllegalStateException ex) {
         super.setStatus(sc);
      }
   }

   public void sendError(int sc, String msg) throws IOException {
      this.copyBodyToResponse(false);

      try {
         super.sendError(sc, msg);
      } catch (IllegalStateException ex) {
         super.setStatus(sc);
      }
   }

   public void sendRedirect(String location) throws IOException {
      this.copyBodyToResponse(false);
      super.sendRedirect(location);
   }

   public ServletOutputStream getOutputStream() throws IOException {
      if (this.outputStream == null) {
         this.outputStream = new ContentCachingResponseWrapper.ResponseServletOutputStream(this.getResponse().getOutputStream());
      }

      return this.outputStream;
   }

   public PrintWriter getWriter() throws IOException {
      if (this.writer == null) {
         String characterEncoding = this.getCharacterEncoding();
         this.writer = characterEncoding != null
            ? new ContentCachingResponseWrapper.ResponsePrintWriter(characterEncoding)
            : new ContentCachingResponseWrapper.ResponsePrintWriter("ISO-8859-1");
      }

      return this.writer;
   }

   public void flushBuffer() throws IOException {
   }

   public void setContentLength(int len) {
      if (len > this.content.size()) {
         this.content.resize(len);
      }

      this.contentLength = len;
   }

   public void setContentLengthLong(long len) {
      this.setContentLength(this.toContentLengthInt(len));
   }

   private int toContentLengthInt(long contentLength) {
      if (contentLength > 2147483647L) {
         throw new IllegalArgumentException("Content-Length exceeds ContentCachingResponseWrapper's maximum (2147483647): " + contentLength);
      } else {
         return (int)contentLength;
      }
   }

   public boolean containsHeader(String name) {
      return this.contentLength != null && "Content-Length".equalsIgnoreCase(name) ? true : super.containsHeader(name);
   }

   public void setHeader(String name, String value) {
      if ("Content-Length".equalsIgnoreCase(name)) {
         if (value != null) {
            this.contentLength = this.toContentLengthInt(Long.parseLong(value));
         } else {
            this.contentLength = null;
            super.setHeader(name, null);
         }
      } else {
         super.setHeader(name, value);
      }
   }

   public void addHeader(String name, String value) {
      if ("Content-Length".equalsIgnoreCase(name)) {
         this.contentLength = this.toContentLengthInt(Long.parseLong(value));
      } else {
         super.addHeader(name, value);
      }
   }

   public void setIntHeader(String name, int value) {
      if ("Content-Length".equalsIgnoreCase(name)) {
         this.contentLength = value;
      } else {
         super.setIntHeader(name, value);
      }
   }

   public void addIntHeader(String name, int value) {
      if ("Content-Length".equalsIgnoreCase(name)) {
         this.contentLength = value;
      } else {
         super.addIntHeader(name, value);
      }
   }

   public @Nullable String getHeader(String name) {
      return this.contentLength != null && "Content-Length".equalsIgnoreCase(name) ? this.contentLength.toString() : super.getHeader(name);
   }

   public Collection<String> getHeaders(String name) {
      return this.contentLength != null && "Content-Length".equalsIgnoreCase(name)
         ? Collections.singleton(this.contentLength.toString())
         : super.getHeaders(name);
   }

   public Collection<String> getHeaderNames() {
      Collection<String> headerNames = super.getHeaderNames();
      if (this.contentLength != null) {
         Set<String> result = new LinkedHashSet<>(headerNames);
         result.add("Content-Length");
         return result;
      } else {
         return headerNames;
      }
   }

   public void setBufferSize(int size) {
      if (size > this.content.size()) {
         this.content.resize(size);
      }
   }

   public void resetBuffer() {
      this.content.reset();
   }

   public void reset() {
      super.reset();
      this.content.reset();
   }

   public byte[] getContentAsByteArray() {
      return this.content.toByteArray();
   }

   public InputStream getContentInputStream() {
      return this.content.getInputStream();
   }

   public int getContentSize() {
      return this.content.size();
   }

   public void copyBodyToResponse() throws IOException {
      this.copyBodyToResponse(true);
   }

   protected void copyBodyToResponse(boolean complete) throws IOException {
      if (this.content.size() > 0) {
         HttpServletResponse rawResponse = (HttpServletResponse)this.getResponse();
         if (!rawResponse.isCommitted() && (complete || this.contentLength != null)) {
            if (rawResponse.getHeader("Transfer-Encoding") == null) {
               rawResponse.setContentLength(complete ? this.content.size() : this.contentLength);
            }

            this.contentLength = null;
         }

         this.content.writeTo(rawResponse.getOutputStream());
         this.content.reset();
         if (complete) {
            super.flushBuffer();
         }
      }
   }

   private class ResponsePrintWriter extends PrintWriter {
      public ResponsePrintWriter(String characterEncoding) throws UnsupportedEncodingException {
         super(new OutputStreamWriter(ContentCachingResponseWrapper.this.content, characterEncoding));
      }

      @Override
      public void write(char[] buf, int off, int len) {
         super.write(buf, off, len);
         super.flush();
      }

      @Override
      public void write(String s, int off, int len) {
         super.write(s, off, len);
         super.flush();
      }

      @Override
      public void write(int c) {
         super.write(c);
         super.flush();
      }
   }

   private class ResponseServletOutputStream extends ServletOutputStream {
      private final ServletOutputStream os;

      public ResponseServletOutputStream(ServletOutputStream os) {
         this.os = os;
      }

      public void write(int b) throws IOException {
         ContentCachingResponseWrapper.this.content.write(b);
      }

      public void write(byte[] b, int off, int len) throws IOException {
         ContentCachingResponseWrapper.this.content.write(b, off, len);
      }

      public boolean isReady() {
         return this.os.isReady();
      }

      public void setWriteListener(WriteListener writeListener) {
         this.os.setWriteListener(writeListener);
      }
   }
}
