package com.example.aibi.query;

import com.example.aibi.auth.CurrentUserProvider;
import com.example.aibi.common.BusinessException;
import com.example.aibi.common.DatabaseRows;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class QueryExportService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final CurrentUserProvider currentUser;

    public QueryExportService(JdbcClient jdbc, ObjectMapper mapper, CurrentUserProvider currentUser) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    public ExportFile export(String queryRunId, String requestedFormat) {
        ExportFormat format = ExportFormat.from(requestedFormat);
        List<Map<String, Object>> matches = jdbc.sql(
                        "SELECT id,question,status,result_json FROM query_run WHERE id=?")
                .param(queryRunId).query().listOfRows();
        if (matches.isEmpty()) {
            throw new BusinessException("QUERY_RUN_NOT_FOUND", "查询记录不存在", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> run = DatabaseRows.normalize(matches.get(0));
        if (!"COMPLETED".equals(String.valueOf(run.get("status")))) {
            throw new BusinessException("QUERY_NOT_COMPLETED", "只有成功完成的查询可以下载",
                    HttpStatus.BAD_REQUEST);
        }
        Integer positiveFeedback = jdbc.sql(
                        "SELECT COUNT(*) FROM query_feedback WHERE query_run_id=? AND rating>=4")
                .param(queryRunId).query(Integer.class).single();
        if (positiveFeedback == null || positiveFeedback == 0) {
            throw new BusinessException("RESULT_NOT_CONFIRMED", "请先确认查询结果正确，再下载报表",
                    HttpStatus.BAD_REQUEST);
        }
        List<Map<String, Object>> rows = parseRows(run.get("result_json"));
        List<String> columns = columns(rows);
        byte[] content = switch (format) {
            case XLSX -> xlsx(rows, columns);
            case CSV -> csv(rows, columns);
            case XML -> xml(rows, columns, String.valueOf(run.get("question")));
        };
        audit(queryRunId, format, rows.size());
        return new ExportFile("query-result-" + queryRunId + "." + format.extension,
                format.contentType, content);
    }

    private List<Map<String, Object>> parseRows(Object jsonValue) {
        if (jsonValue == null || String.valueOf(jsonValue).isBlank()) {
            throw new BusinessException("RESULT_SNAPSHOT_MISSING", "该历史查询没有结果快照，请重新提问后下载",
                    HttpStatus.BAD_REQUEST);
        }
        try {
            return mapper.readValue(String.valueOf(jsonValue), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("查询结果快照解析失败", ex);
        }
    }

    private List<String> columns(List<Map<String, Object>> rows) {
        Set<String> result = new LinkedHashSet<>();
        rows.forEach(row -> result.addAll(row.keySet()));
        return new ArrayList<>(result);
    }

    private byte[] csv(List<Map<String, Object>> rows, List<String> columns) {
        StringBuilder content = new StringBuilder("\uFEFF");
        content.append(columns.stream().map(this::csvValue).reduce((a, b) -> a + "," + b).orElse(""))
                .append("\r\n");
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) content.append(',');
                content.append(csvValue(row.get(columns.get(i))));
            }
            content.append("\r\n");
        }
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvValue(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private byte[] xml(List<Map<String, Object>> rows, List<String> columns, String question) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output, "UTF-8");
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement("queryReport");
            writer.writeStartElement("question");
            writer.writeCharacters(question);
            writer.writeEndElement();
            writer.writeStartElement("rows");
            writer.writeAttribute("count", String.valueOf(rows.size()));
            for (Map<String, Object> row : rows) {
                writer.writeStartElement("row");
                for (String column : columns) {
                    writer.writeStartElement("column");
                    writer.writeAttribute("name", column);
                    Object value = row.get(column);
                    if (value != null) writer.writeCharacters(String.valueOf(value));
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("XML 报表生成失败", ex);
        }
    }

    private byte[] xlsx(List<Map<String, Object>> rows, List<String> columns) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("查询结果");
            sheet.createFreezePane(0, 1);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row excelRow = sheet.createRow(rowIndex + 1);
                Map<String, Object> source = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    Object value = source.get(columns.get(columnIndex));
                    Cell cell = excelRow.createCell(columnIndex);
                    if (value instanceof Number number) cell.setCellValue(number.doubleValue());
                    else if (value instanceof Boolean bool) cell.setCellValue(bool);
                    else cell.setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
            for (int i = 0; i < columns.size(); i++) {
                int maxLength = columns.get(i).length();
                for (Map<String, Object> row : rows) {
                    Object value = row.get(columns.get(i));
                    if (value != null) maxLength = Math.max(maxLength, String.valueOf(value).length());
                }
                sheet.setColumnWidth(i, Math.min(Math.max(maxLength + 3, 12), 50) * 256);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Excel 报表生成失败", ex);
        }
    }

    private void audit(String queryRunId, ExportFormat format, int rowCount) {
        try {
            jdbc.sql("INSERT INTO audit_event(trace_id,event_type,actor,resource_type,resource_id,detail) VALUES(?,'QUERY_EXPORTED',?,'QUERY_RUN',?,?)")
                    .params(queryRunId, currentUser.username(), queryRunId,
                            mapper.writeValueAsString(Map.of("format", format.name(), "rowCount", rowCount))).update();
        } catch (Exception ignored) {
            // Export should not fail only because audit serialization is unavailable.
        }
    }

    public record ExportFile(String fileName, String contentType, byte[] content) {}

    private enum ExportFormat {
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        CSV("csv", "text/csv; charset=UTF-8"),
        XML("xml", "application/xml; charset=UTF-8");

        private final String extension;
        private final String contentType;

        ExportFormat(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        static ExportFormat from(String value) {
            try {
                return ExportFormat.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("UNSUPPORTED_EXPORT_FORMAT",
                        "不支持的下载格式，可选：xlsx、csv、xml", HttpStatus.BAD_REQUEST);
            }
        }
    }
}
