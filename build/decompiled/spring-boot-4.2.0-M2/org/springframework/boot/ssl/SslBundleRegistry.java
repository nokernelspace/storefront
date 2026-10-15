package org.springframework.boot.ssl;

public interface SslBundleRegistry {
   void registerBundle(String name, SslBundle bundle);

   void updateBundle(String name, SslBundle updatedBundle) throws NoSuchSslBundleException;
}
