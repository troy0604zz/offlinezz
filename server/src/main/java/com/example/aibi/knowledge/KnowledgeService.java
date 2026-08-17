package com.example.aibi.knowledge;

import com.example.aibi.common.BusinessException;
import com.example.aibi.common.DatabaseRows;
import com.example.aibi.config.AiBiProperties;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
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
    private final Tika tika = new Tika();

    public KnowledgeService(VectorStorePort vectorStore, JdbcClient jdbc, AiBiProperties properties) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.storageRoot = Path.of(properties.storage().root()).toAbsolutePath().normalize();
    }

    public Map<String, Object> upload(String domain, MultipartFile file) {
        if (file.isEmpty()) throw new BusinessException("EMPTY_FILE", "文件不能为空", HttpStatus.BAD_REQUEST);
        try {
            Files.createDirectories(storageRoot);
            String safeName = Path.of(file.getOriginalFilename() == null ? "document.bin" : file.getOriginalFilename()).getFileName().toString();
            String storageName = UUID.randomUUID() + "-" + safeName;
            Path target = storageRoot.resolve(storageName).normalize();
            if (!target.startsWith(storageRoot)) throw new IllegalArgumentException("非法文件名");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String content = tika.parseToString(target);
            List<KnowledgeChunk> chunks = chunk(storageName, content, domain, safeName);
            vectorStore.upsert(domain, chunks);
            String version = Instant.now().toString();
            jdbc.sql("INSERT INTO knowledge_document(file_name,content_type,storage_path,status,index_version,domain) VALUES(?,?,?,?,?,?)")
                    .params(safeName, file.getContentType(), target.toString(), "PUBLISHED", version, domain)
                    .update();
            Long id = jdbc.sql("SELECT id FROM knowledge_document WHERE storage_path=?")
                    .param(target.toString()).query(Long.class).single();
            return Map.of("id", id, "fileName", safeName, "chunks", chunks.size(), "indexVersion", version,
                    "vectorProvider", vectorStore.providerName());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("DOCUMENT_PARSE_FAILED", "文档解析或索引失败：" + ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public List<KnowledgeChunk> search(String domain, String query, int topK) {
        return vectorStore.search(domain, query, Math.min(Math.max(topK, 1), 20), Map.of("domain", domain));
    }

    public List<Map<String, Object>> listDocuments() {
        return DatabaseRows.normalize(jdbc.sql("SELECT id,file_name,content_type,domain,status,index_version,created_at FROM knowledge_document ORDER BY id DESC")
                .query().listOfRows());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restorePublishedIndex() {
        List<Map<String,Object>> docs=DatabaseRows.normalize(jdbc.sql("SELECT file_name,storage_path,domain FROM knowledge_document WHERE status='PUBLISHED'").query().listOfRows());
        for(Map<String,Object> doc:docs) {
            try {
                Path path=Path.of(String.valueOf(doc.get("storage_path")));
                if(!Files.exists(path)) continue;
                String domain=String.valueOf(doc.get("domain"));
                String content=tika.parseToString(path);
                vectorStore.upsert(domain,chunk(path.getFileName().toString(),content,domain,String.valueOf(doc.get("file_name"))));
            } catch(Exception ignored) {
                // Keep application available; the admin list exposes the document for re-indexing.
            }
        }
    }

    private List<KnowledgeChunk> chunk(String prefix, String content, String domain, String fileName) {
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
                result.add(new KnowledgeChunk(prefix + "-" + index, text,
                        Map.of("domain", domain, "fileName", fileName, "chunkIndex", index), 1.0));
            }
            if (end >= cleaned.length()) break;
            start = Math.max(end - overlap, start + 1);
            index++;
        }
        return result;
    }
}
