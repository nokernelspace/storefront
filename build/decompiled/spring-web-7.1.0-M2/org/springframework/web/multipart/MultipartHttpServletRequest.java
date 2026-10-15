package org.springframework.web.multipart;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public interface MultipartHttpServletRequest extends HttpServletRequest, MultipartRequest {
   HttpMethod getRequestMethod();

   HttpHeaders getRequestHeaders();

   @Nullable HttpHeaders getMultipartHeaders(String paramOrFileName);
}
