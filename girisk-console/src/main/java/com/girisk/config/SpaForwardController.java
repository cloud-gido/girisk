package com.girisk.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/login", "/girisk", "/girisk/**", "/sports", "/sports/**",
            "/stream", "/evaluate", "/api-lab", "/rules", "/strategies",
            "/cases", "/lists", "/events", "/decisions", "/sports-bet"
    })
    public String forwardSpa() {
        return "forward:/index.html";
    }
}
