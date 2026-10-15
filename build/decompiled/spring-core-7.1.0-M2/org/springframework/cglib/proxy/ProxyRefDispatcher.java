package org.springframework.cglib.proxy;

public interface ProxyRefDispatcher extends Callback {
   Object loadObject(Object proxy) throws Exception;
}
