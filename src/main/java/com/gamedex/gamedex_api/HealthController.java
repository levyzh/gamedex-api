package com.gamedex.gamedex_api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController tells Spring: this class handles web requests, and
// whatever its methods return should be sent back as the response body.
// Spring finds this class automatically at startup just by scanning the
// package — we never register it anywhere. That's "component scanning".
@RestController
public class HealthController {

    // @GetMapping maps an HTTP GET for this exact path to this method.
    // Browser hits /api/health → Spring calls health() → the returned
    // String IS the response.
    @GetMapping("/api/health")
    public String health() {
        return "gamedex-api is alive";
    }
}