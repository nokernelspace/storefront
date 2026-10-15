package org.springframework.core.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ReflectionUtils;

public abstract class VfsUtils {
   private static final String VFS3_PKG = "org.jboss.vfs.";
   private static final String VFS_NAME = "VFS";
   private static final Method VFS_METHOD_GET_ROOT_URL;
   private static final Method VFS_METHOD_GET_ROOT_URI;
   private static final Method VIRTUAL_FILE_METHOD_EXISTS;
   private static final Method VIRTUAL_FILE_METHOD_GET_INPUT_STREAM;
   private static final Method VIRTUAL_FILE_METHOD_GET_SIZE;
   private static final Method VIRTUAL_FILE_METHOD_GET_LAST_MODIFIED;
   private static final Method VIRTUAL_FILE_METHOD_TO_URL;
   private static final Method VIRTUAL_FILE_METHOD_TO_URI;
   private static final Method VIRTUAL_FILE_METHOD_GET_NAME;
   private static final Method VIRTUAL_FILE_METHOD_GET_PATH_NAME;
   private static final Method VIRTUAL_FILE_METHOD_GET_PHYSICAL_FILE;
   private static final Method VIRTUAL_FILE_METHOD_GET_CHILD;
   protected static final Class<?> VIRTUAL_FILE_VISITOR_INTERFACE;
   protected static final Method VIRTUAL_FILE_METHOD_VISIT;
   private static final Field VISITOR_ATTRIBUTES_FIELD_RECURSE;

   protected static Object invokeVfsMethod(Method method, @Nullable Object target, Object... args) throws IOException {
      try {
         return method.invoke(target, args);
      } catch (InvocationTargetException ex) {
         if (ex.getTargetException() instanceof IOException ioException) {
            throw ioException;
         }

         ReflectionUtils.handleInvocationTargetException(ex);
      } catch (Exception ex) {
         ReflectionUtils.handleReflectionException(ex);
      }

      throw new IllegalStateException("Invalid code path reached");
   }

   static boolean exists(Object vfsResource) {
      try {
         return (Boolean)invokeVfsMethod(VIRTUAL_FILE_METHOD_EXISTS, vfsResource);
      } catch (IOException ex) {
         return false;
      }
   }

   static boolean isReadable(Object vfsResource) {
      try {
         return (Long)invokeVfsMethod(VIRTUAL_FILE_METHOD_GET_SIZE, vfsResource) > 0L;
      } catch (IOException ex) {
         return false;
      }
   }

   static long getSize(Object vfsResource) throws IOException {
      return (Long)invokeVfsMethod(VIRTUAL_FILE_METHOD_GET_SIZE, vfsResource);
   }

   static long getLastModified(Object vfsResource) throws IOException {
      return (Long)invokeVfsMethod(VIRTUAL_FILE_METHOD_GET_LAST_MODIFIED, vfsResource);
   }

   static InputStream getInputStream(Object vfsResource) throws IOException {
      return (InputStream)invokeVfsMethod(VIRTUAL_FILE_METHOD_GET_INPUT_STREAM, vfsResource);
   }

   static URL getURL(Object vfsResource) throws IOException {
      return (URL)invokeVfsMethod(VIRTUAL_FILE_METHOD_TO_URL, vfsResource);
   }

   static URI getURI(Object vfsResource) throws IOException {
      return (URI)invokeVfsMethod(VIRTUAL_FILE_METHOD_TO_URI, vfsResource);
   }

   static String getName(Object vfsResource) {
      try {
         return (String)invokeVfsMethod(VIRTUAL_FILE_METHOD_GET_NAME, vfsResource);
      } catch (IOException ex) {
         throw new IllegalStateException("Cannot get resource name", ex);
      }
   }

   static Object getRelative(URL url) throws IOException {
      return invokeVfsMethod(VFS_METHOD_GET_ROOT_URL, null, url);
   }

   static Object getChild(Object vfsResource, String path) throws IOException {
      return invokeVfsMethod(VIRTUAL_FILE_METHOD_GET_CHILD, vfsResource, path);
   }

   static File getFile(Object vfsResource) throws IOException {
      return (File)invokeVfsMethod(VIRTUAL_FILE_METHOD_GET_PHYSICAL_FILE, vfsResource);
   }

   static Object getRoot(URI url) throws IOException {
      return invokeVfsMethod(VFS_METHOD_GET_ROOT_URI, null, url);
   }

   protected static Object getRoot(URL url) throws IOException {
      return invokeVfsMethod(VFS_METHOD_GET_ROOT_URL, null, url);
   }

   protected static @Nullable Object doGetVisitorAttributes() {
      return ReflectionUtils.getField(VISITOR_ATTRIBUTES_FIELD_RECURSE, null);
   }

   protected static @Nullable String doGetPath(Object resource) {
      return (String)ReflectionUtils.invokeMethod(VIRTUAL_FILE_METHOD_GET_PATH_NAME, resource);
   }

   static {
      ClassLoader loader = VfsUtils.class.getClassLoader();

      try {
         Class<?> vfsClass = loader.loadClass("org.jboss.vfs.VFS");
         VFS_METHOD_GET_ROOT_URL = vfsClass.getMethod("getChild", URL.class);
         VFS_METHOD_GET_ROOT_URI = vfsClass.getMethod("getChild", URI.class);
         Class<?> virtualFile = loader.loadClass("org.jboss.vfs.VirtualFile");
         VIRTUAL_FILE_METHOD_EXISTS = virtualFile.getMethod("exists");
         VIRTUAL_FILE_METHOD_GET_INPUT_STREAM = virtualFile.getMethod("openStream");
         VIRTUAL_FILE_METHOD_GET_SIZE = virtualFile.getMethod("getSize");
         VIRTUAL_FILE_METHOD_GET_LAST_MODIFIED = virtualFile.getMethod("getLastModified");
         VIRTUAL_FILE_METHOD_TO_URI = virtualFile.getMethod("toURI");
         VIRTUAL_FILE_METHOD_TO_URL = virtualFile.getMethod("toURL");
         VIRTUAL_FILE_METHOD_GET_NAME = virtualFile.getMethod("getName");
         VIRTUAL_FILE_METHOD_GET_PATH_NAME = virtualFile.getMethod("getPathName");
         VIRTUAL_FILE_METHOD_GET_PHYSICAL_FILE = virtualFile.getMethod("getPhysicalFile");
         VIRTUAL_FILE_METHOD_GET_CHILD = virtualFile.getMethod("getChild", String.class);
         VIRTUAL_FILE_VISITOR_INTERFACE = loader.loadClass("org.jboss.vfs.VirtualFileVisitor");
         VIRTUAL_FILE_METHOD_VISIT = virtualFile.getMethod("visit", VIRTUAL_FILE_VISITOR_INTERFACE);
         Class<?> visitorAttributesClass = loader.loadClass("org.jboss.vfs.VisitorAttributes");
         VISITOR_ATTRIBUTES_FIELD_RECURSE = visitorAttributesClass.getField("RECURSE");
      } catch (Throwable ex) {
         throw new IllegalStateException("Could not detect JBoss VFS infrastructure", ex);
      }
   }
}
