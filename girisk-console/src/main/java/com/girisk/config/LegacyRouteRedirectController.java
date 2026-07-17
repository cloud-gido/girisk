package com.girisk.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class LegacyRouteRedirectController {

    @GetMapping("/stream")
    public RedirectView stream() {
        return new RedirectView("/girisk/stream");
    }

    @GetMapping("/evaluate")
    public RedirectView evaluate() {
        return new RedirectView("/girisk/evaluate");
    }

    @GetMapping("/events")
    public RedirectView events() {
        return new RedirectView("/girisk/events");
    }

    @GetMapping("/decisions")
    public RedirectView decisions() {
        return new RedirectView("/girisk/decisions");
    }

    @GetMapping("/rules")
    public RedirectView rules() {
        return new RedirectView("/girisk/rules");
    }

    @GetMapping("/strategies")
    public RedirectView strategies() {
        return new RedirectView("/girisk/strategies");
    }

    @GetMapping("/lists")
    public RedirectView lists() {
        return new RedirectView("/girisk/lists");
    }

    @GetMapping("/cases")
    public RedirectView cases() {
        return new RedirectView("/girisk/cases");
    }

    @GetMapping("/api-lab")
    public RedirectView apiLab() {
        return new RedirectView("/girisk/api-lab");
    }

    @GetMapping("/sports-bet")
    public RedirectView sportsBet() {
        return new RedirectView("/sports/bet");
    }
}
