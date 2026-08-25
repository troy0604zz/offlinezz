package com.example.aibi.knowledge;

import com.example.aibi.common.BusinessException;
import com.example.aibi.common.DatabaseRows;
import com.example.aibi.config.AiBiProperties;
import com.example.aibi.domain.DomainAccessService;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Service
public class KnowledgeService {
    private final VectorStorePort vectorStore;
    private final JdbcClient jdbc;
    private final Path storageRoot;
    private final DomainAccessService access;
    private final Tika tika = new Tika();

    public KnowledgeService(VectorStorePort vectorStore, JdbcClient jdbc, AiBiProperties properties, DomainAccessService access) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.storageRoot = Path.of(properties.storage().root()).toAbsolutePath().normalize();
        this.access = access;
    }

    @Transactional
    public Map<String, Object> upload(String domain, MultipartFile file) {
        domain = access.requireTrain(domain);
        if (file.isEmpty()) throw new BusinessException("EMPTY_FILE", "文件不能为空", HttpStatus.BAD_REQUEST);
        Path target=null;
        try {
            Files.createDirectories(storageRoot);
            String safeName = Path.of(file.getOriginalFilename() == null ? "document.bin" : file.getOriginalFilename()).getFileName().toString();
            String storageName = UUID.randomUUID() + "-" + safeName;
            target = storageRoot.resolve(storageName).normalize();
            if (!target.startsWith(storageRoot)) throw new IllegalArgumentException("非法文件名");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String content = tika.parseToString(target);
            String version = Instant.now().toString();
            jdbc.sql("INSERT INTO knowledge_document(file_name,content_type,storage_path,status,index_version,domain) VALUES(?,?,?,?,?,?)")
                    .params(safeName, file.getContentType(), target.toString(), "PUBLISHED", version, domain)
                    .update();
            Long id = jdbc.sql("SELECT id FROM knowledge_document WHERE storage_path=?")
                    .param(target.toString()).query(Long.class).single();
            List<KnowledgeChunk> chunks = documentChunks(id, content, domain, safeName, version);
            vectorStore.invalidateOtherEmbeddings(domain);
            vectorStore.upsert(domain, chunks);
            return Map.of("id", id, "fileName", safeName, "chunks", chunks.size(), "indexVersion", version,
                    "vectorProvider", vectorStore.providerName());
        } catch (BusinessException ex) {
            cleanupFile(target);
            throw ex;
        } catch (Exception ex) {
            cleanupFile(target);
            throw new BusinessException("DOCUMENT_PARSE_FAILED", "文档解析或索引失败：" + ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public List<KnowledgeChunk> search(String domain, String query, int topK) {
        domain = access.requireTrain(domain);
        return vectorStore.search(domain, query, Math.min(Math.max(topK, 1), 20), Map.of("domain", domain));
    }

    public List<Map<String, Object>> listDocuments(String rawDomain) {
        String domain=access.requireTrain(rawDomain);
        return DatabaseRows.normalize(jdbc.sql("SELECT id,file_name,content_type,domain,status,index_version,created_at FROM knowledge_document WHERE domain=? ORDER BY id DESC")
                .param(domain).query().listOfRows());
    }

    @Transactional
    public Map<String,Object> update(long id,String rawDomain,String fileName,String status) {
        String domain=access.requireTrain(rawDomain); requireOwned(id,domain);
        String safeName=Path.of(fileName==null?"":fileName).getFileName().toString();
        if(safeName.isBlank()) throw new BusinessException("VALIDATION_FAILED","文件名称不能为空",HttpStatus.BAD_REQUEST);
        String normalizedStatus="DISABLED".equalsIgnoreCase(status)?"DISABLED":"PUBLISHED";
        jdbc.sql("UPDATE knowledge_document SET file_name=?,status=? WHERE id=? AND domain=?").params(safeName,normalizedStatus,id,domain).update();
        rebuildDomain(domain);
        return Map.of("id",id,"updated",true);
    }

    @Transactional
    public Map<String,Object> delete(long id,String rawDomain) {
        String domain=access.requireTrain(rawDomain); Map<String,Object> row=requireOwned(id,domain);
        // Rebuild first and explicitly exclude this document. A Qdrant failure aborts the database delete,
        // so the API can never report success while stale document vectors remain searchable.
        rebuildDomain(domain,id);
        jdbc.sql("DELETE FROM knowledge_document WHERE id=? AND domain=?").params(id,domain).update();
        boolean storageFileDeleted=false;
        String storageWarning=null;
        try {
            Path storagePath=Path.of(String.valueOf(row.get("storage_path")));
            Files.deleteIfExists(storagePath);
            storageFileDeleted=!Files.exists(storagePath);
        } catch(Exception ex) {
            // The logical document and all searchable vectors are already gone. A locked orphan file is not
            // allowed to resurrect knowledge or turn a successful knowledge deletion into a misleading failure.
            storageWarning="原始文件暂未清理，请检查存储目录权限："+ex.getMessage();
        }
        Map<String,Object> result=new java.util.LinkedHashMap<>();
        result.put("id",id);result.put("deleted",true);result.put("vectorIndexRebuilt",true);
        result.put("vectorProvider",vectorStore.providerName());result.put("storageFileDeleted",storageFileDeleted);
        if(storageWarning!=null) result.put("warning",storageWarning);
        return result;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restorePublishedIndex() {
        List<String> domains=jdbc.sql("SELECT code FROM data_domain WHERE status='ACTIVE'").query(String.class).list();
        for(String domain:domains) {
            try { rebuildDomain(domain); }
            catch(Exception ignored) {
                // External vector storage may start after the API. Admin operations/search will still expose the error.
            }
        }
    }

    public void rebuildAllDomains() {
        for(String domain:jdbc.sql("SELECT code FROM data_domain WHERE status='ACTIVE'").query(String.class).list()) rebuildDomain(domain);
    }

    public void rebuildDomain(String domain) {
        rebuildDomain(domain,null);
    }

    private void rebuildDomain(String domain,Long excludedDocumentId) {
        vectorStore.clear(domain);
        List<KnowledgeChunk> projections=new ArrayList<>();
        List<Map<String,Object>> docs=DatabaseRows.normalize(jdbc.sql("SELECT id,file_name,storage_path,domain,index_version FROM knowledge_document WHERE status='PUBLISHED' AND domain=?").param(domain).query().listOfRows());
        for(Map<String,Object> doc:docs) {
            if(excludedDocumentId!=null&&((Number)doc.get("id")).longValue()==excludedDocumentId) continue;
            List<KnowledgeChunk> chunks;
            String docDomain=String.valueOf(doc.get("domain"));
            try {
                Path path=Path.of(String.valueOf(doc.get("storage_path")));
                if(!Files.exists(path)) continue;
                String content=tika.parseToString(path);
                chunks=documentChunks(((Number)doc.get("id")).longValue(),content,docDomain,
                        String.valueOf(doc.get("file_name")),String.valueOf(doc.get("index_version")));
            } catch(Exception ignored) {
                // Keep application available; the admin list exposes the document for re-indexing.
                continue;
            }
            projections.addAll(chunks);
        }
        List<Map<String,Object>> schemas=DatabaseRows.normalize(jdbc.sql("SELECT id,name,ddl_text,version FROM schema_asset WHERE status='PUBLISHED' AND domain=?").param(domain).query().listOfRows());
        for(Map<String,Object> schema:schemas) {
            String schemaId="schema-"+schema.get("id");
            projections.add(new KnowledgeChunk(schemaId,String.valueOf(schema.get("ddl_text")),
                    metadata(domain,VectorAssetType.SCHEMA,schema.get("id"),schema.get("version"),"PUBLISHED",
                            Map.of("name",String.valueOf(schema.get("name")))),1));
        }
        List<Map<String,Object>> metrics=DatabaseRows.normalize(jdbc.sql("SELECT id,business_code,name,description,base_table,version FROM semantic_metric WHERE status='PUBLISHED' AND domain=?").param(domain).query().listOfRows());
        for(Map<String,Object> metric:metrics) {
            String content="业务指标："+metric.get("name")+"\n指标编码："+metric.get("business_code")+
                    "\n业务说明："+text(metric.get("description"))+"\n基础表："+metric.get("base_table");
            projections.add(new KnowledgeChunk("metric-"+metric.get("id"),content,
                    metadata(domain,VectorAssetType.METRIC,metric.get("id"),metric.get("version"),"PUBLISHED",
                            Map.of("code",String.valueOf(metric.get("business_code")),"name",String.valueOf(metric.get("name")))),1));
        }
        List<Map<String,Object>> examples=DatabaseRows.normalize(jdbc.sql("SELECT id,question,explanation,version FROM sql_example WHERE status='PUBLISHED' AND domain=?").param(domain).query().listOfRows());
        for(Map<String,Object> example:examples) {
            String content="管理员已审核标准问题："+example.get("question")+"\n说明："+text(example.get("explanation"));
            projections.add(new KnowledgeChunk("sql-example-"+example.get("id"),content,
                    metadata(domain,VectorAssetType.SQL_EXAMPLE,example.get("id"),example.get("version"),"PUBLISHED",
                            Map.of("question",String.valueOf(example.get("question")))),1));
        }
        // Keep embedding requests bounded for local models while avoiding one network round trip per asset.
        for(int start=0;start<projections.size();start+=32)
            vectorStore.upsert(domain,new ArrayList<>(projections.subList(start,Math.min(start+32,projections.size()))));
        vectorStore.invalidateOtherEmbeddings(domain);
    }

    private Map<String,Object> requireOwned(long id,String domain) {
        List<Map<String,Object>> rows=jdbc.sql("SELECT id,storage_path FROM knowledge_document WHERE id=? AND domain=?").params(id,domain).query().listOfRows();
        if(rows.isEmpty()) throw new BusinessException("DOCUMENT_NOT_FOUND","域内知识文档不存在",HttpStatus.NOT_FOUND);
        return DatabaseRows.normalize(rows.get(0));
    }

    private List<KnowledgeChunk> documentChunks(long assetId,String content,String domain,String fileName,String version) {
        String cleaned = content.replaceAll("\\r", "").replaceAll("\\n{3,}", "\n\n").trim();
        if (cleaned.isBlank()) throw new IllegalArgumentException("未提取到可索引文本");
        int max = 900;
        int overlap = 120;
        List<KnowledgeChunk> result = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < cleaned.length()) {
            int end = Math.min(start + max, cleaned.length());
            if (end < cleaned.length()) {
                int boundary = cleaned.lastIndexOf('\n', end);
                if (boundary > start + max / 2) end = boundary;
            }
            String text = cleaned.substring(start, end).trim();
            if (!text.isBlank()) {
                result.add(new KnowledgeChunk("document-"+assetId+"-"+index,text,
                        metadata(domain,VectorAssetType.DOCUMENT,assetId,version,"PUBLISHED",
                                Map.of("fileName",fileName,"chunkIndex",index)),1.0));
            }
            if (end >= cleaned.length()) break;
            start = Math.max(end - overlap, start + 1);
            index++;
        }
        return result;
    }

    private Map<String,Object> metadata(String domain,VectorAssetType type,Object assetId,Object version,
                                        String status,Map<String,Object> details) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("domain",domain);result.put("assetType",type.name());result.put("assetId",String.valueOf(assetId));
        result.put("assetVersion",String.valueOf(version));result.put("status",status);result.putAll(details);
        return result;
    }

    private String text(Object value) { return value==null?"":String.valueOf(value); }

    private void cleanupFile(Path target) {
        if(target==null) return;
        try { Files.deleteIfExists(target); } catch(Exception ignored) { }
    }
}
