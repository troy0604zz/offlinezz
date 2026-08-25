package com.example.aibi.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/knowledge")
public class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) { this.service = service; }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam(defaultValue = "sales") String domain,
                                      @RequestPart MultipartFile file) {
        return service.upload(domain, file);
    }

    @GetMapping("/documents")
    public List<Map<String, Object>> documents(@RequestParam String domain) { return service.listDocuments(domain); }

    @PutMapping("/documents/{id}")
    public Map<String,Object> update(@PathVariable long id,@RequestParam String domain,@RequestBody DocumentUpdate request) {
        return service.update(id,domain,request.fileName(),request.status());
    }

    @DeleteMapping("/documents/{id}")
    public Map<String,Object> delete(@PathVariable long id,@RequestParam String domain) { return service.delete(id,domain); }

    @GetMapping("/search")
    public List<KnowledgeChunk> search(@RequestParam(defaultValue = "sales") String domain,
                                       @RequestParam @NotBlank String query,
                                       @RequestParam(defaultValue = "5") @Min(1) @Max(20) int topK) {
        return service.search(domain, query, topK);
    }

    public record DocumentUpdate(String fileName,String status) {}
}
