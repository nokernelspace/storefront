package io.github.mimerme;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

// This driver is for 

@Controller
public class Storefront {
    @GetMapping("/")
    public String hello() {
        return "index"; // Maps to templates/index.html
    }

    @PostMapping("/")
    public String world(){
        return "index";
    }
}