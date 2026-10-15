package org.springframework.beans.factory;

public interface SmartInitializingSingleton {
   void afterSingletonsInstantiated();
}
