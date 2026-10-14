package io.github.mimerme.yaros;

public class SpringUtils {
    public boolean is_allow_memory_leaks(){
        return Boolean.parseBoolean(System.getProperty("spring.main.allow-circular-references"));
    }
}
