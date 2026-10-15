package org.springframework.web.multipart;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.MultiValueMap;

public interface MultipartRequest {
   Iterator<String> getFileNames();

   @Nullable MultipartFile getFile(String name);

   List<MultipartFile> getFiles(String name);

   Map<String, MultipartFile> getFileMap();

   MultiValueMap<String, MultipartFile> getMultiFileMap();

   @Nullable String getMultipartContentType(String paramOrFileName);
}
