package io.github.mimerme.core;

import java.io.Serializable;
import java.util.function.Supplier;

@FunctionalInterface 
public interface RouteHandler extends Supplier<String>, Serializable {

}