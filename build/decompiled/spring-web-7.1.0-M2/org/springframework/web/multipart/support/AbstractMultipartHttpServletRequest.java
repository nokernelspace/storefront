package org.springframework.web.multipart.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

public abstract class AbstractMultipartHttpServletRequest extends HttpServletRequestWrapper implements MultipartHttpServletRequest {
   private @Nullable MultiValueMap<String, MultipartFile> multipartFiles;

   protected AbstractMultipartHttpServletRequest(HttpServletRequest request) {
      super(request);
   }

   public HttpServletRequest getRequest() {
      return (HttpServletRequest)super.getRequest();
   }

   @Override
   public HttpMethod getRequestMethod() {
      return HttpMethod.valueOf(this.getRequest().getMethod());
   }

   @Override
   public HttpHeaders getRequestHeaders() {
      HttpHeaders headers = new HttpHeaders();
      Enumeration<String> headerNames = this.getHeaderNames();

      while (headerNames.hasMoreElements()) {
         String headerName = headerNames.nextElement();
         headers.put(headerName, Collections.list(this.getHeaders(headerName)));
      }

      return headers;
   }

   @Override
   public Iterator<String> getFileNames() {
      return this.getMultipartFiles().keySet().iterator();
   }

   @Override
   public @Nullable MultipartFile getFile(String name) {
      return (MultipartFile)this.getMultipartFiles().getFirst(name);
   }

   @Override
   public List<MultipartFile> getFiles(String name) {
      List<MultipartFile> multipartFiles = (List<MultipartFile>)this.getMultipartFiles().get(name);
      return multipartFiles != null ? multipartFiles : Collections.emptyList();
   }

   @Override
   public Map<String, MultipartFile> getFileMap() {
      return this.getMultipartFiles().asSingleValueMap();
   }

   @Override
   public MultiValueMap<String, MultipartFile> getMultiFileMap() {
      return this.getMultipartFiles();
   }

   public boolean isResolved() {
      return this.multipartFiles != null;
   }

   protected final void setMultipartFiles(MultiValueMap<String, MultipartFile> multipartFiles) {
      this.multipartFiles = new LinkedMultiValueMap(Collections.unmodifiableMap(multipartFiles));
   }

   protected MultiValueMap<String, MultipartFile> getMultipartFiles() {
      if (this.multipartFiles == null) {
         this.initializeMultipart();
      }

      return (MultiValueMap<String, MultipartFile>)(this.multipartFiles != null ? this.multipartFiles : new LinkedMultiValueMap(Collections.emptyMap()));
   }

   protected void initializeMultipart() {
      throw new IllegalStateException("Multipart request not initialized");
   }
}
