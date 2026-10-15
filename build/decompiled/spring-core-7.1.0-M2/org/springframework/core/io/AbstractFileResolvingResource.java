package org.springframework.core.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.springframework.util.ResourceUtils;

public abstract class AbstractFileResolvingResource extends AbstractResource {
   @Override
   public boolean exists() {
      try {
         URL url = this.getURL();
         if (ResourceUtils.isFileURL(url)) {
            return this.getFile().exists();
         }

         URLConnection con = url.openConnection();
         this.customizeConnection(con);
         HttpURLConnection httpCon = con instanceof HttpURLConnection huc ? huc : null;
         if (httpCon != null) {
            httpCon.setRequestMethod("HEAD");
            int code = httpCon.getResponseCode();
            if (code == 200) {
               return true;
            }

            if (code == 404) {
               return false;
            }

            if (code == 405) {
               con = url.openConnection();
               this.customizeConnection(con);
               if (con instanceof HttpURLConnection newHttpCon) {
                  code = newHttpCon.getResponseCode();
                  if (code == 200) {
                     return true;
                  }

                  if (code == 404) {
                     return false;
                  }

                  httpCon = newHttpCon;
               }
            }
         }

         if (con instanceof JarURLConnection jarCon) {
            JarFile jarFile = jarCon.getJarFile();

            try {
               return jarCon.getEntryName() == null || jarCon.getJarEntry() != null;
            } finally {
               if (!jarCon.getUseCaches()) {
                  jarFile.close();
               }
            }
         } else if (con.getContentLengthLong() > 0L) {
            return true;
         } else if (httpCon != null) {
            httpCon.disconnect();
            return false;
         } else {
            this.getInputStream().close();
            return true;
         }
      } catch (IOException ex) {
         return false;
      }
   }

   @Override
   public boolean isReadable() {
      try {
         return this.checkReadable(this.getURL());
      } catch (IOException ex) {
         return false;
      }
   }

   boolean checkReadable(URL url) {
      try {
         if (!ResourceUtils.isFileURL(url)) {
            URLConnection con = url.openConnection();
            this.customizeConnection(con);
            if (con instanceof HttpURLConnection httpCon) {
               httpCon.setRequestMethod("HEAD");
               int code = httpCon.getResponseCode();
               if (code == 405) {
                  con = url.openConnection();
                  this.customizeConnection(con);
                  if (!(con instanceof HttpURLConnection newHttpCon)) {
                     return false;
                  }

                  code = newHttpCon.getResponseCode();
                  httpCon = newHttpCon;
               }

               if (code != 200) {
                  httpCon.disconnect();
                  return false;
               }
            } else if (con instanceof JarURLConnection jarCon) {
               JarEntry jarEntry = jarCon.getJarEntry();
               if (jarEntry == null) {
                  return false;
               }

               return !jarEntry.isDirectory();
            }

            long contentLength = con.getContentLengthLong();
            if (contentLength > 0L) {
               return true;
            }

            if (contentLength == 0L) {
               return false;
            }

            this.getInputStream().close();
            return true;
         } else {
            File file = this.getFile();
            return file.canRead() && !file.isDirectory();
         }
      } catch (IOException ex) {
         return false;
      }
   }

   @Override
   public boolean isFile() {
      try {
         URL url = this.getURL();
         return url.getProtocol().startsWith("vfs")
            ? AbstractFileResolvingResource.VfsResourceDelegate.getResource(url).isFile()
            : "file".equals(url.getProtocol());
      } catch (IOException ex) {
         return false;
      }
   }

   @Override
   public File getFile() throws IOException {
      URL url = this.getURL();
      return url.getProtocol().startsWith("vfs")
         ? AbstractFileResolvingResource.VfsResourceDelegate.getResource(url).getFile()
         : ResourceUtils.getFile(url, this.getDescription());
   }

   @Override
   protected File getFileForLastModifiedCheck() throws IOException {
      URL url = this.getURL();
      if (ResourceUtils.isJarURL(url)) {
         URL actualUrl = ResourceUtils.extractArchiveURL(url);
         return actualUrl.getProtocol().startsWith("vfs")
            ? AbstractFileResolvingResource.VfsResourceDelegate.getResource(actualUrl).getFile()
            : ResourceUtils.getFile(actualUrl, "Jar URL");
      } else {
         return this.getFile();
      }
   }

   protected boolean isFile(URI uri) {
      try {
         return uri.getScheme().startsWith("vfs")
            ? AbstractFileResolvingResource.VfsResourceDelegate.getResource(uri).isFile()
            : "file".equals(uri.getScheme());
      } catch (IOException ex) {
         return false;
      }
   }

   protected File getFile(URI uri) throws IOException {
      return uri.getScheme().startsWith("vfs")
         ? AbstractFileResolvingResource.VfsResourceDelegate.getResource(uri).getFile()
         : ResourceUtils.getFile(uri, this.getDescription());
   }

   @Override
   public ReadableByteChannel readableChannel() throws IOException {
      try {
         return FileChannel.open(this.getFile().toPath(), StandardOpenOption.READ);
      } catch (FileNotFoundException | NoSuchFileException ex) {
         return super.readableChannel();
      }
   }

   @Override
   public long contentLength() throws IOException {
      URL url = this.getURL();
      if (ResourceUtils.isFileURL(url)) {
         File file = this.getFile();
         long length = file.length();
         if (length == 0L && !file.exists()) {
            throw new FileNotFoundException(this.getDescription() + " cannot be resolved in the file system for checking its content length");
         } else {
            return length;
         }
      } else {
         URLConnection con = url.openConnection();
         this.customizeConnection(con);
         if (con instanceof HttpURLConnection httpCon) {
            httpCon.setRequestMethod("HEAD");
         }

         long length = con.getContentLengthLong();
         if (length <= 0L && con instanceof HttpURLConnection httpCon && httpCon.getResponseCode() == 405) {
            con = url.openConnection();
            this.customizeConnection(con);
            length = con.getContentLengthLong();
         }

         return length;
      }
   }

   @Override
   public long lastModified() throws IOException {
      URL url = this.getURL();
      boolean fileCheck = false;
      if (ResourceUtils.isFileURL(url) || ResourceUtils.isJarURL(url)) {
         fileCheck = true;

         try {
            File fileToCheck = this.getFileForLastModifiedCheck();
            long lastModified = fileToCheck.lastModified();
            if (lastModified > 0L || fileToCheck.exists()) {
               return lastModified;
            }
         } catch (FileNotFoundException var7) {
         }
      }

      URLConnection con = url.openConnection();
      this.customizeConnection(con);
      if (con instanceof HttpURLConnection httpCon) {
         httpCon.setRequestMethod("HEAD");
      }

      long lastModified = con.getLastModified();
      if (lastModified == 0L) {
         if (con instanceof HttpURLConnection httpCon && httpCon.getResponseCode() == 405) {
            con = url.openConnection();
            this.customizeConnection(con);
            lastModified = con.getLastModified();
         }

         if (fileCheck && con.getContentLengthLong() <= 0L) {
            throw new FileNotFoundException(this.getDescription() + " cannot be resolved in the file system for checking its last-modified timestamp");
         }
      }

      return lastModified;
   }

   protected void customizeConnection(URLConnection con) throws IOException {
      this.useCachesIfNecessary(con);
      if (con instanceof HttpURLConnection httpCon) {
         this.customizeConnection(httpCon);
      }
   }

   void useCachesIfNecessary(URLConnection con) {
      ResourceUtils.useCachesIfNecessary(con);
   }

   protected void customizeConnection(HttpURLConnection con) throws IOException {
   }

   private static class VfsResourceDelegate {
      public static Resource getResource(URL url) throws IOException {
         return new VfsResource(VfsUtils.getRoot(url));
      }

      public static Resource getResource(URI uri) throws IOException {
         return new VfsResource(VfsUtils.getRoot(uri));
      }
   }
}
