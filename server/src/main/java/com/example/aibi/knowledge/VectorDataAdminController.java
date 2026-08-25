package com.example.aibi.knowledge;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/vector-data")
public class VectorDataAdminController {
    private final VectorDataAdminService service;

    public VectorDataAdminController(VectorDataAdminService service) {
        this.service = service;
    }

    @GetMapping("/purge-preview")
    public Map<String, Object> preview(@RequestParam(required = false) String domain,
                                       @RequestParam(required = false) String assetType) {
        return service.preview(domain, assetType);
    }

    @PostMapping("/purge")
    public Map<String, Object> purge(@RequestBody @Valid VectorDataPurgeRequest request) {
        return service.purge(request);
    }
}
