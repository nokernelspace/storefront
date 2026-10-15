package org.springframework.cglib.proxy;

public interface NoOp extends Callback {
   NoOp INSTANCE = new NoOp() {};
}
