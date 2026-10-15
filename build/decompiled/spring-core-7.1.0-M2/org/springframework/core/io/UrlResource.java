package org.springframework.core.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

public class UrlResource extends AbstractFileResolvingResource {
   private static final String AUTHORIZATION = "Authorization";
   private final @Nullable URI uri;
   private final URL url;
   private volatile @Nullable String cleanedUrl;
   volatile @Nullable Boolean useCaches;

   public UrlResource(URL url) {
      Assert.notNull(url, "URL must not be null");
      this.uri = null;
      this.url = url;
   }

   public UrlResource(URI uri) throws MalformedURLException {
      Assert.notNull(uri, "URI must not be null");
      this.uri = uri;
      this.url = uri.toURL();
   }

   public UrlResource(String path) throws MalformedURLException {
      Assert.notNull(path, "Path must not be null");
      String cleanedPath = StringUtils.cleanPath(path);

      URI uri;
      URL url;
      try {
         uri = ResourceUtils.toURI(cleanedPath);
         url = uri.toURL();
      } catch (URISyntaxException | IllegalArgumentException ex) {
         uri = null;
         url = ResourceUtils.toURL(path);
      }

      this.uri = uri;
      this.url = url;
      this.cleanedUrl = cleanedPath;
   }

   public UrlResource(String protocol, String location) throws MalformedURLException {
      this(protocol, location, null);
   }

   public UrlResource(String protocol, String location, @Nullable String fragment) throws MalformedURLException {
      try {
         this.uri = new URI(protocol, location, fragment);
         this.url = this.uri.toURL();
      } catch (URISyntaxException ex) {
         MalformedURLException exToThrow = new MalformedURLException(ex.getMessage());
         exToThrow.initCause(ex);
         throw exToThrow;
      }
   }

   public static UrlResource from(URI uri) throws UncheckedIOException {
      try {
         return new UrlResource(uri);
      } catch (MalformedURLException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   public static UrlResource from(String path) throws UncheckedIOException {
      try {
         return new UrlResource(path);
      } catch (MalformedURLException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   private String getCleanedUrl() {
      String cleanedUrl = this.cleanedUrl;
      if (cleanedUrl != null) {
         return cleanedUrl;
      }

      String originalPath = (this.uri != null ? this.uri : this.url).toString();
      cleanedUrl = StringUtils.cleanPath(originalPath);
      this.cleanedUrl = cleanedUrl;
      return cleanedUrl;
   }

   public void setUseCaches(boolean useCaches) {
      this.useCaches = useCaches;
   }

   @Override
   public InputStream getInputStream() throws IOException {
      URLConnection con = this.url.openConnection();
      this.customizeConnection(con);

      try {
         return con.getInputStream();
      } catch (IOException ex) {
         if (con instanceof HttpURLConnection httpCon) {
            httpCon.disconnect();
         }

         throw ex;
      }
   }

   @Override
   protected void customizeConnection(URLConnection con) throws IOException {
      super.customizeConnection(con);
      String userInfo = this.url.getUserInfo();
      if (userInfo != null) {
         String encodedCredentials = Base64.getUrlEncoder().encodeToString(userInfo.getBytes());
         con.setRequestProperty("Authorization", "Basic " + encodedCredentials);
      }
   }

   @Override
   void useCachesIfNecessary(URLConnection con) {
      Boolean useCaches = this.useCaches;
      if (useCaches != null) {
         con.setUseCaches(useCaches);
      } else {
         super.useCachesIfNecessary(con);
      }
   }

   @Override
   public URL getURL() {
      return this.url;
   }

   @Override
   public URI getURI() throws IOException {
      return this.uri != null ? this.uri : super.getURI();
   }

   @Override
   public boolean isFile() {
      return this.uri != null ? super.isFile(this.uri) : super.isFile();
   }

   @Override
   public File getFile() throws IOException {
      return this.uri != null ? super.getFile(this.uri) : super.getFile();
   }

   @Override
   public Resource createRelative(String relativePath) throws MalformedURLException {
      UrlResource resource = new UrlResource(this.createRelativeURL(relativePath));
      resource.useCaches = this.useCaches;
      return resource;
   }

   protected URL createRelativeURL(String relativePath) throws MalformedURLException {
      if (relativePath.startsWith("/")) {
         relativePath = relativePath.substring(1);
      }

      return ResourceUtils.toRelativeURL(this.url, relativePath);
   }

   @Override
   public @Nullable String getFilename() {
      if (this.uri != null) {
         String path = this.uri.getPath();
         if (path != null) {
            return StringUtils.getFilename(this.uri.getPath());
         }
      }

      String filename = StringUtils.getFilename(StringUtils.cleanPath(this.url.getPath()));
      return filename != null ? URLDecoder.decode(filename, StandardCharsets.UTF_8) : null;
   }

   @Override
   public String getDescription() {
      return "URL [" + (this.uri != null ? this.uri : this.url) + "]";
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof UrlResource that && this.getCleanedUrl().equals(that.getCleanedUrl());
   }

   @Override
   public int hashCode() {
      return this.getCleanedUrl().hashCode();
   }
}
