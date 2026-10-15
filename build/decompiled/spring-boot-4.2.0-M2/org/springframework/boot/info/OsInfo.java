package org.springframework.boot.info;

public class OsInfo {
   private final String name = System.getProperty("os.name");
   private final String version = System.getProperty("os.version");
   private final String arch = System.getProperty("os.arch");

   public String getName() {
      return this.name;
   }

   public String getVersion() {
      return this.version;
   }

   public String getArch() {
      return this.arch;
   }
}
