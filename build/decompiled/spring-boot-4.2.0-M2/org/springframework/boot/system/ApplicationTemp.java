package org.springframework.boot.system;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.springframework.core.NativeDetector;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class ApplicationTemp {
   private static final FileAttribute<?>[] NO_FILE_ATTRIBUTES = new FileAttribute[0];
   private static final EnumSet<PosixFilePermission> DIRECTORY_PERMISSIONS = EnumSet.of(
      PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE
   );
   private final @Nullable Class<?> sourceClass;
   private final Lock pathLock = new ReentrantLock();
   private final @Nullable UserPrincipal directoryOwner;
   private volatile @Nullable Path path;

   public ApplicationTemp() {
      this(null);
   }

   public ApplicationTemp(@Nullable Class<?> sourceClass) {
      this.sourceClass = sourceClass;
      this.directoryOwner = directoryOwner();
   }

   private static @Nullable UserPrincipal directoryOwner() {
      try {
         Path tempFile = Files.createTempFile("application-temp", "-owner");
         UserPrincipal owner = Files.getOwner(tempFile);
         Files.delete(tempFile);
         return owner;
      } catch (UnsupportedOperationException ex) {
         return null;
      } catch (IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   @Override
   public String toString() {
      return this.getDir().getAbsolutePath();
   }

   public File getDir() {
      return this.getPath().toFile();
   }

   public File getDir(String subDir) {
      return this.createDirectory(this.getPath().resolve(subDir)).toFile();
   }

   public Path getPath() {
      if (this.path == null) {
         this.pathLock.lock();

         try {
            if (this.path == null) {
               String hash = HexFormat.of().withUpperCase().formatHex(this.generateHash(this.sourceClass));
               this.path = this.createDirectory(this.getTempDirectory().resolve(hash));
            }
         } finally {
            this.pathLock.unlock();
         }
      }

      Path path = this.path;
      Assert.state(path != null, "'path' must not be null");
      return path;
   }

   private Path createDirectory(Path path) {
      try {
         FileSystem fileSystem = path.getFileSystem();
         if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(path, this.asFileAttributes(fileSystem, DIRECTORY_PERMISSIONS));
         } else if (this.supportsPosixView(fileSystem)) {
            PosixFileAttributes attributes = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Assert.state(attributes.isDirectory(), () -> "'" + path + "' already exists but it is not a directory");
            Assert.state(
               DIRECTORY_PERMISSIONS.equals(attributes.permissions()),
               () -> "Existing directory '" + path + "' does not have the permissions " + DIRECTORY_PERMISSIONS
            );
            this.assertDirectoryOwnership(attributes.owner(), path);
         } else {
            Assert.state(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS), () -> "'" + path + "' already exists but it is not a directory");

            try {
               this.assertDirectoryOwnership(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS), path);
            } catch (UnsupportedOperationException var4) {
            }
         }

         return path;
      } catch (IOException ex) {
         throw new IllegalStateException("Unable to create application temp directory " + path, ex);
      }
   }

   private void assertDirectoryOwnership(UserPrincipal owner, Path path) {
      if (this.directoryOwner != null) {
         Assert.state(this.directoryOwner.equals(owner), () -> "Existing directory '" + path + "' is not owned by " + this.directoryOwner.getName());
      }
   }

   private FileAttribute<?>[] asFileAttributes(FileSystem fileSystem, EnumSet<PosixFilePermission> ownerReadWrite) {
      return !this.supportsPosixView(fileSystem) ? NO_FILE_ATTRIBUTES : new FileAttribute[]{PosixFilePermissions.asFileAttribute(ownerReadWrite)};
   }

   private boolean supportsPosixView(FileSystem fileSystem) {
      return fileSystem.supportedFileAttributeViews().contains("posix");
   }

   private Path getTempDirectory() {
      String property = System.getProperty("java.io.tmpdir");
      Assert.state(StringUtils.hasLength(property), "No 'java.io.tmpdir' property set");
      Path tempDirectory = Paths.get(property);
      Assert.state(Files.exists(tempDirectory), () -> "Temp directory '" + tempDirectory + "' does not exist");
      Assert.state(Files.isDirectory(tempDirectory), () -> "Temp location '" + tempDirectory + "' is not a directory");
      return tempDirectory;
   }

   private byte[] generateHash(@Nullable Class<?> sourceClass) {
      ApplicationHome home = new ApplicationHome(sourceClass);

      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-1");
         this.update(digest, home.getSource());
         this.update(digest, home.getDir());
         this.update(digest, System.getProperty("user.dir"));
         if (!NativeDetector.inNativeImage()) {
            this.update(digest, System.getProperty("java.home"));
         }

         this.update(digest, System.getProperty("java.class.path"));
         this.update(digest, System.getProperty("sun.java.command"));
         this.update(digest, System.getProperty("sun.boot.class.path"));
         return digest.digest();
      } catch (Exception ex) {
         throw new IllegalStateException(ex);
      }
   }

   private void update(MessageDigest digest, @Nullable Object source) {
      if (source != null) {
         digest.update(this.getUpdateSourceBytes(source));
      }
   }

   private byte[] getUpdateSourceBytes(Object source) {
      return source instanceof File file ? this.getUpdateSourceBytes(file.getAbsolutePath()) : source.toString().getBytes();
   }
}
