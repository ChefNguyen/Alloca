package com.alloca.resource.controller;

import com.alloca.resource.service.ResourceService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for resources and operating schedules.
 *
 * <p>TODO (ALC-29 to ALC-33): implement routes from OpenAPI after building the
 * resource model, repository and schedule validation service.
 */
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }
}
