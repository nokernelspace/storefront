package io.github.mimerme;

public class YarosSpringUtils {
    public boolean is_allow_memory_leaks(){
        return Boolean.parseBoolean(System.getProperty("spring.main.allow-circular-references"));
    }
}
