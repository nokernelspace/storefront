package org.springframework.boot.info;

public class JavaInfo {
   private final String version = System.getProperty("java.version");
   private final JavaInfo.JavaVendorInfo vendor = new JavaInfo.JavaVendorInfo();
   private final JavaInfo.JavaRuntimeEnvironmentInfo runtime = new JavaInfo.JavaRuntimeEnvironmentInfo();
   private final JavaInfo.JavaVirtualMachineInfo jvm = new JavaInfo.JavaVirtualMachineInfo();

   public String getVersion() {
      return this.version;
   }

   public JavaInfo.JavaVendorInfo getVendor() {
      return this.vendor;
   }

   public JavaInfo.JavaRuntimeEnvironmentInfo getRuntime() {
      return this.runtime;
   }

   public JavaInfo.JavaVirtualMachineInfo getJvm() {
      return this.jvm;
   }

   public static class JavaRuntimeEnvironmentInfo {
      private final String name = System.getProperty("java.runtime.name");
      private final String version = System.getProperty("java.runtime.version");

      public String getName() {
         return this.name;
      }

      public String getVersion() {
         return this.version;
      }
   }

   public static class JavaVendorInfo {
      private final String name = System.getProperty("java.vendor");
      private final String version = System.getProperty("java.vendor.version");

      public String getName() {
         return this.name;
      }

      public String getVersion() {
         return this.version;
      }
   }

   public static class JavaVirtualMachineInfo {
      private final String name = System.getProperty("java.vm.name");
      private final String vendor = System.getProperty("java.vm.vendor");
      private final String version = System.getProperty("java.vm.version");

      public String getName() {
         return this.name;
      }

      public String getVendor() {
         return this.vendor;
      }

      public String getVersion() {
         return this.version;
      }
   }
}
