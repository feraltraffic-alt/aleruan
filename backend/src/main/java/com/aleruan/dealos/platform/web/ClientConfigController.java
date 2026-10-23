package com.aleruan.dealos.platform.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings the browser needs at runtime, so keys can be changed on the server without rebuilding the
 * frontend. The Google Maps key is a browser key by design: protect it with an HTTP-referrer restriction.
 */
@RestController
class ClientConfigController {

    record ClientConfig(String googleMapsApiKey, String googleMapsMapId) {
    }

    private final ClientConfig config;

    ClientConfigController(@Value("${dealos.maps.google-api-key:}") String key,
                           @Value("${dealos.maps.google-map-id:}") String mapId) {
        this.config = new ClientConfig(key.isBlank() ? null : key.strip(), mapId.isBlank() ? null : mapId.strip());
    }

    @GetMapping("/api/config")
    ClientConfig config() {
        return config;
    }
}
