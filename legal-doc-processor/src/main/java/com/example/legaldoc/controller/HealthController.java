package com.example.legaldoc.controller;

import com.example.legaldoc.health.HealthCheckService;
import com.example.legaldoc.model.ServiceHealthStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthCheckService healthCheckService;

    @GetMapping("/services")
    public ServiceHealthStatus services() {
        return healthCheckService.checkAll();
    }
}
