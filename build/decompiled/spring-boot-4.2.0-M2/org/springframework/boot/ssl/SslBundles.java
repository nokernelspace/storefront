package org.springframework.boot.ssl;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface SslBundles {
   SslBundle getBundle(String name) throws NoSuchSslBundleException;

   void addBundleUpdateHandler(String name, Consumer<SslBundle> updateHandler) throws NoSuchSslBundleException;

   void addBundleRegisterHandler(BiConsumer<String, SslBundle> registerHandler);

   List<String> getBundleNames();
}
