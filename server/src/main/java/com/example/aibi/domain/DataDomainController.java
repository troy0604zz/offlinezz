package com.example.aibi.domain;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class DataDomainController {
    private final DataDomainService domains;
    private final DomainDataSourceService dataSources;

    public DataDomainController(DataDomainService domains, DomainDataSourceService dataSources) {
        this.domains = domains;
        this.dataSources = dataSources;
    }

    @GetMapping("/domains")
    public List<Map<String, Object>> mine() { return domains.mine(); }

    @PostMapping("/admin/domains")
    public Map<String, Object> create(@RequestBody DataDomainService.DomainRequest request) { return domains.create(request); }

    @PutMapping("/admin/domains/{code}")
    public Map<String, Object> update(@PathVariable String code, @RequestBody DataDomainService.DomainRequest request) { return domains.update(code, request); }

    @GetMapping("/admin/domains/{code}/datasource")
    public Map<String, Object> dataSource(@PathVariable String code) { return dataSources.get(code); }

    @PutMapping("/admin/domains/{code}/datasource")
    public Map<String, Object> updateDataSource(@PathVariable String code, @RequestBody DomainDataSourceService.UpdateRequest request) {
        return dataSources.update(code, request);
    }

    @PostMapping("/admin/domains/{code}/datasource/test")
    public Map<String, Object> testDataSource(@PathVariable String code) { return dataSources.test(code); }

    @GetMapping("/admin/domains/{code}/members")
    public List<Map<String, Object>> members(@PathVariable String code) { return domains.members(code); }

    @PutMapping("/admin/domains/{code}/members")
    public Map<String, Object> member(@PathVariable String code, @RequestBody DataDomainService.MemberRequest request) {
        return domains.upsertMember(code, request);
    }

    @DeleteMapping("/admin/domains/{code}/members/{userId}")
    public Map<String, Object> deleteMember(@PathVariable String code, @PathVariable long userId) {
        return domains.deleteMember(code, userId);
    }
}
