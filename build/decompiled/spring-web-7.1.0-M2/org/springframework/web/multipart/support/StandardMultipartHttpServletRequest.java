package org.springframework.web.multipart.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

public class StandardMultipartHttpServletRequest extends AbstractMultipartHttpServletRequest {
   private @Nullable Set<String> multipartParameterNames;

   public StandardMultipartHttpServletRequest(HttpServletRequest request) throws MultipartException {
      this(request, false);
   }

   public StandardMultipartHttpServletRequest(HttpServletRequest request, boolean lazyParsing) throws MultipartException {
      super(request);
      if (!lazyParsing) {
         this.parseRequest(request);
      }
   }

   private void parseRequest(HttpServletRequest request) {
      try {
         Collection<Part> parts = request.getParts();
         this.multipartParameterNames = CollectionUtils.newLinkedHashSet(parts.size());
         MultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap(parts.size());

         for (Part part : parts) {
            String headerValue = part.getHeader("Content-Disposition");
            ContentDisposition disposition = ContentDisposition.parse(headerValue);
            String filename = disposition.getFilename();
            if (filename != null) {
               files.add(part.getName(), new StandardMultipartHttpServletRequest.StandardMultipartFile(part, filename));
            } else {
               this.multipartParameterNames.add(part.getName());
            }
         }

         this.setMultipartFiles(files);
      } catch (Throwable ex) {
         this.handleParseFailure(ex);
      }
   }

   protected void handleParseFailure(Throwable ex) {
      Throwable cause = ex;

      do {
         String msg = cause.toString();
         if (msg != null) {
            msg = msg.toLowerCase(Locale.ROOT);
            if ((msg.contains("exceed") || msg.contains("limit")) && (msg.contains("size") || msg.contains("length") || msg.contains("count"))
               || msg.contains("request") && (msg.contains("big") || msg.contains("large"))) {
               throw new MaxUploadSizeExceededException(-1L, ex);
            }
         }

         cause = cause.getCause();
      } while (cause != null);

      throw new MultipartException("Failed to parse multipart servlet request", ex);
   }

   @Override
   protected void initializeMultipart() {
      this.parseRequest(this.getRequest());
   }

   public Enumeration<String> getParameterNames() {
      if (this.multipartParameterNames == null) {
         this.initializeMultipart();
      }

      if (CollectionUtils.isEmpty(this.multipartParameterNames)) {
         return super.getParameterNames();
      }

      Set<String> paramNames = new LinkedHashSet<>();
      Enumeration<String> paramEnum = super.getParameterNames();

      while (paramEnum.hasMoreElements()) {
         paramNames.add(paramEnum.nextElement());
      }

      paramNames.addAll(this.multipartParameterNames);
      return Collections.enumeration(paramNames);
   }

   public Map<String, String[]> getParameterMap() {
      if (this.multipartParameterNames == null) {
         this.initializeMultipart();
      }

      if (CollectionUtils.isEmpty(this.multipartParameterNames)) {
         return super.getParameterMap();
      }

      Map<String, String[]> paramMap = new LinkedHashMap<>(super.getParameterMap());

      for (String paramName : this.multipartParameterNames) {
         if (!paramMap.containsKey(paramName)) {
            paramMap.put(paramName, this.getParameterValues(paramName));
         }
      }

      return paramMap;
   }

   @Override
   public @Nullable String getMultipartContentType(String paramOrFileName) {
      try {
         Part part = this.getPart(paramOrFileName);
         return part != null ? part.getContentType() : null;
      } catch (Throwable ex) {
         throw new MultipartException("Could not access multipart servlet request", ex);
      }
   }

   @Override
   public @Nullable HttpHeaders getMultipartHeaders(String paramOrFileName) {
      try {
         Part part = this.getPart(paramOrFileName);
         if (part == null) {
            return null;
         }

         HttpHeaders headers = new HttpHeaders();

         for (String headerName : part.getHeaderNames()) {
            headers.put(headerName, new ArrayList<>(part.getHeaders(headerName)));
         }

         return headers;
      } catch (Throwable ex) {
         throw new MultipartException("Could not access multipart servlet request", ex);
      }
   }

   private static class StandardMultipartFile implements MultipartFile, Serializable {
      private final Part part;
      private final String filename;

      public StandardMultipartFile(Part part, String filename) {
         this.part = part;
         this.filename = filename;
      }

      @Override
      public String getName() {
         return this.part.getName();
      }

      @Override
      public String getOriginalFilename() {
         return this.filename;
      }

      @Override
      public String getContentType() {
         return this.part.getContentType();
      }

      @Override
      public boolean isEmpty() {
         return this.part.getSize() == 0L;
      }

      @Override
      public long getSize() {
         return this.part.getSize();
      }

      @Override
      public byte[] getBytes() throws IOException {
         return FileCopyUtils.copyToByteArray(this.part.getInputStream());
      }

      @Override
      public InputStream getInputStream() throws IOException {
         return this.part.getInputStream();
      }

      @Override
      public void transferTo(File dest) throws IOException, IllegalStateException {
         this.part.write(dest.getPath());
         if (dest.isAbsolute() && !dest.exists()) {
            FileCopyUtils.copy(this.part.getInputStream(), Files.newOutputStream(dest.toPath()));
         }
      }

      @Override
      public void transferTo(Path dest) throws IOException, IllegalStateException {
         FileCopyUtils.copy(this.part.getInputStream(), Files.newOutputStream(dest));
      }
   }
}
