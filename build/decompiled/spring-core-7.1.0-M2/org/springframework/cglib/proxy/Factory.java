package org.springframework.cglib.proxy;

public interface Factory {
   Object newInstance(Callback callback);

   Object newInstance(Callback[] callbacks);

   Object newInstance(Class[] types, Object[] args, Callback[] callbacks);

   Callback getCallback(int index);

   void setCallback(int index, Callback callback);

   void setCallbacks(Callback[] callbacks);

   Callback[] getCallbacks();
}
