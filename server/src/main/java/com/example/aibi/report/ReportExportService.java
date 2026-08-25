package com.example.aibi.report;

import com.example.aibi.auth.CurrentUserProvider;
import com.example.aibi.common.BusinessException;
import com.example.aibi.common.DatabaseRows;
import com.example.aibi.domain.DomainAccessService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ReportExportService {
    private static final int WORD_ROW_LIMIT = 100;
    private static final int PDF_ROW_LIMIT = 30;
    private static final int PDF_COLUMN_LIMIT = 8;

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final DomainAccessService access;
    private final CurrentUserProvider currentUser;

    public ReportExportService(JdbcClient jdbc, ObjectMapper mapper, DomainAccessService access,
                               CurrentUserProvider currentUser) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.access = access;
        this.currentUser = currentUser;
    }

    public ExportFile export(String id, String requestedFormat) {
        ExportFormat format = ExportFormat.from(requestedFormat);
        List<Map<String, Object>> matches = jdbc.sql(
                        "SELECT id,domain,title,status,content_json FROM report_job WHERE id=?")
                .param(id).query().listOfRows();
        if (matches.isEmpty()) {
            throw new BusinessException("REPORT_NOT_FOUND", "报告不存在", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> row = DatabaseRows.normalize(matches.get(0));
        access.requireReport(text(row.get("domain")));
        if (!"READY".equals(text(row.get("status")))) {
            throw new BusinessException("REPORT_NOT_READY", "只有已完成的报告可以下载", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> report = parseReport(row.get("content_json"));
        byte[] content = switch (format) {
            case PDF -> pdf(report);
            case DOCX -> docx(report);
        };
        audit(id, format);
        String title = safeFileName(text(report.get("title")));
        if (title.isBlank()) title = "智能分析报告-" + id;
        return new ExportFile(title + "." + format.extension, format.contentType, content);
    }

    private Map<String, Object> parseReport(Object json) {
        if (json == null || text(json).isBlank()) {
            throw new BusinessException("REPORT_SNAPSHOT_MISSING", "该历史报告没有内容快照，请重新生成", HttpStatus.BAD_REQUEST);
        }
        try {
            return mapper.readValue(text(json), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("报告历史内容解析失败", ex);
        }
    }

    private byte[] docx(Map<String, Object> report) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            run(title, text(report.get("title")), 22, true);
            XWPFParagraph metadata = document.createParagraph();
            metadata.setAlignment(ParagraphAlignment.CENTER);
            run(metadata, metadata(report), 9, false);

            heading(document, "执行摘要", 1);
            body(document, text(report.get("executiveSummary")));
            if (!text(report.get("request")).isBlank()) {
                heading(document, "分析要求", 2);
                body(document, text(report.get("request")));
            }
            addWarningsDocx(document, strings(report.get("warnings")));

            int index = 1;
            for (Map<String, Object> section : maps(report.get("sections"))) {
                heading(document, index + ". " + text(section.get("title")), 1);
                if (!text(section.get("question")).isBlank()) {
                    labelBody(document, "分析问题：", text(section.get("question")));
                }
                Map<String, Object> query = map(section.get("query"));
                if (!query.isEmpty()) {
                    body(document, text(query.get("answer")));
                    addWordTable(document, maps(query.get("rows")));
                    if (!text(query.get("sql")).isBlank()) {
                        heading(document, "查询依据 SQL", 2);
                        code(document, text(query.get("sql")));
                    }
                } else if (!text(section.get("error")).isBlank()) {
                    labelBody(document, "本章节未完成：", text(section.get("error")));
                }
                index++;
            }

            List<String> recommendations = strings(report.get("recommendations"));
            if (!recommendations.isEmpty()) {
                heading(document, "行动建议", 1);
                for (int i = 0; i < recommendations.size(); i++) {
                    body(document, (i + 1) + ". " + recommendations.get(i));
                }
            }
            document.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Word 报告生成失败：" + ex.getMessage(), ex);
        }
    }

    private byte[] pdf(Map<String, Object> report) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 34, 34, 34, 34);
            PdfWriter.getInstance(document, output);
            BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(baseFont, 20, Font.BOLD, new Color(27, 49, 77));
            Font headingFont = new Font(baseFont, 15, Font.BOLD, new Color(27, 79, 136));
            Font subheadingFont = new Font(baseFont, 11, Font.BOLD, new Color(42, 64, 89));
            Font bodyFont = new Font(baseFont, 10, Font.NORMAL, new Color(55, 67, 82));
            Font mutedFont = new Font(baseFont, 8, Font.NORMAL, new Color(105, 119, 136));
            Font tableFont = new Font(baseFont, 7, Font.NORMAL, Color.DARK_GRAY);
            Font tableHeaderFont = new Font(baseFont, 7, Font.BOLD, new Color(27, 49, 77));

            document.open();
            Paragraph title = paragraph(text(report.get("title")), titleFont, 10);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            Paragraph metadata = paragraph(metadata(report), mutedFont, 20);
            metadata.setAlignment(Element.ALIGN_CENTER);
            document.add(metadata);

            addPdfHeading(document, "执行摘要", headingFont);
            document.add(paragraph(text(report.get("executiveSummary")), bodyFont, 12));
            if (!text(report.get("request")).isBlank()) {
                addPdfHeading(document, "分析要求", subheadingFont);
                document.add(paragraph(text(report.get("request")), bodyFont, 12));
            }
            List<String> warnings = strings(report.get("warnings"));
            if (!warnings.isEmpty()) {
                addPdfHeading(document, "生成提示", subheadingFont);
                for (String warning : warnings) document.add(paragraph("• " + warning, bodyFont, 4));
            }

            int index = 1;
            for (Map<String, Object> section : maps(report.get("sections"))) {
                addPdfHeading(document, index + ". " + text(section.get("title")), headingFont);
                if (!text(section.get("question")).isBlank()) {
                    document.add(paragraph("分析问题：" + text(section.get("question")), mutedFont, 6));
                }
                Map<String, Object> query = map(section.get("query"));
                if (!query.isEmpty()) {
                    document.add(paragraph(text(query.get("answer")), bodyFont, 8));
                    addPdfTable(document, maps(query.get("rows")), tableFont, tableHeaderFont, mutedFont);
                    if (!text(query.get("sql")).isBlank()) {
                        addPdfHeading(document, "查询依据 SQL", subheadingFont);
                        document.add(paragraph(text(query.get("sql")), mutedFont, 10));
                    }
                } else if (!text(section.get("error")).isBlank()) {
                    document.add(paragraph("本章节未完成：" + text(section.get("error")), bodyFont, 8));
                }
                index++;
            }

            List<String> recommendations = strings(report.get("recommendations"));
            if (!recommendations.isEmpty()) {
                addPdfHeading(document, "行动建议", headingFont);
                for (int i = 0; i < recommendations.size(); i++) {
                    document.add(paragraph((i + 1) + ". " + recommendations.get(i), bodyFont, 5));
                }
            }
            document.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 报告生成失败：" + ex.getMessage(), ex);
        }
    }

    private void addWarningsDocx(XWPFDocument document, List<String> warnings) {
        if (warnings.isEmpty()) return;
        heading(document, "生成提示", 2);
        for (String warning : warnings) labelBody(document, "• ", warning);
    }

    private void addWordTable(XWPFDocument document, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        List<String> columns = columns(rows);
        if (columns.isEmpty()) return;
        int displayedRows = Math.min(rows.size(), WORD_ROW_LIMIT);
        XWPFTable table = document.createTable(displayedRows + 1, columns.size());
        for (int column = 0; column < columns.size(); column++) {
            setCell(table.getRow(0).getCell(column), columns.get(column), true);
        }
        for (int row = 0; row < displayedRows; row++) {
            for (int column = 0; column < columns.size(); column++) {
                setCell(table.getRow(row + 1).getCell(column), text(rows.get(row).get(columns.get(column))), false);
            }
        }
        if (rows.size() > WORD_ROW_LIMIT) {
            body(document, "结果共 " + rows.size() + " 行，Word 中仅展示前 " + WORD_ROW_LIMIT + " 行。");
        }
    }

    private void addPdfTable(Document document, List<Map<String, Object>> rows, Font tableFont,
                             Font tableHeaderFont, Font mutedFont) throws Exception {
        if (rows.isEmpty()) return;
        List<String> allColumns = columns(rows);
        if (allColumns.isEmpty()) return;
        List<String> shownColumns = allColumns.subList(0, Math.min(allColumns.size(), PDF_COLUMN_LIMIT));
        PdfPTable table = new PdfPTable(shownColumns.size());
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        table.setSpacingAfter(8);
        for (String column : shownColumns) {
            PdfPCell cell = new PdfPCell(new Phrase(column, tableHeaderFont));
            cell.setBackgroundColor(new Color(231, 240, 250));
            cell.setPadding(5);
            table.addCell(cell);
        }
        table.setHeaderRows(1);
        int displayedRows = Math.min(rows.size(), PDF_ROW_LIMIT);
        for (int row = 0; row < displayedRows; row++) {
            for (String column : shownColumns) {
                PdfPCell cell = new PdfPCell(new Phrase(text(rows.get(row).get(column)), tableFont));
                cell.setPadding(4);
                table.addCell(cell);
            }
        }
        document.add(table);
        if (rows.size() > PDF_ROW_LIMIT || allColumns.size() > PDF_COLUMN_LIMIT) {
            document.add(paragraph("表格内容较多，PDF 仅展示前 " + PDF_ROW_LIMIT + " 行、前 " + PDF_COLUMN_LIMIT + " 列。", mutedFont, 8));
        }
    }

    private void heading(XWPFDocument document, String value, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(level == 1 ? 260 : 120);
        paragraph.setSpacingAfter(80);
        run(paragraph, value, level == 1 ? 16 : 12, true);
    }

    private void body(XWPFDocument document, String value) {
        if (value.isBlank()) return;
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(100);
        run(paragraph, value, 10, false);
    }

    private void labelBody(XWPFDocument document, String label, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(80);
        run(paragraph, label, 10, true);
        run(paragraph, value, 10, false);
    }

    private void code(XWPFDocument document, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Consolas");
        run.setFontSize(9);
        run.setText(value);
    }

    private void run(XWPFParagraph paragraph, String value, int size, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(size);
        run.setBold(bold);
        run.setText(value == null ? "" : value);
    }

    private void setCell(XWPFTableCell cell, String value, boolean header) {
        cell.setText(value);
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            for (XWPFRun run : paragraph.getRuns()) {
                run.setFontFamily("Microsoft YaHei");
                run.setFontSize(8);
                run.setBold(header);
            }
        }
    }

    private void addPdfHeading(Document document, String value, Font font) throws Exception {
        document.add(paragraph(value, font, 7));
    }

    private Paragraph paragraph(String value, Font font, float spacingAfter) {
        Paragraph paragraph = new Paragraph(value == null ? "" : value, font);
        paragraph.setLeading(15);
        paragraph.setSpacingAfter(spacingAfter);
        return paragraph;
    }

    private String metadata(Map<String, Object> report) {
        List<String> parts = new ArrayList<>();
        if (!text(report.get("domain")).isBlank()) parts.add("数据域：" + text(report.get("domain")));
        if (!text(report.get("generatedAt")).isBlank()) parts.add("生成时间：" + text(report.get("generatedAt")));
        if (!text(report.get("generatedBy")).isBlank()) parts.add("创建人：" + text(report.get("generatedBy")));
        if (!text(report.get("id")).isBlank()) parts.add("报告编号：" + text(report.get("id")));
        return String.join("    ", parts);
    }

    private List<String> columns(List<Map<String, Object>> rows) {
        Set<String> result = new LinkedHashSet<>();
        rows.forEach(row -> result.addAll(row.keySet()));
        return new ArrayList<>(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(item -> item != null && !text(item).isBlank()).map(this::text).toList();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeFileName(String value) {
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "-").trim();
        cleaned = cleaned.replaceAll("[. ]+$", "");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private void audit(String id, ExportFormat format) {
        try {
            jdbc.sql("INSERT INTO audit_event(trace_id,event_type,actor,resource_type,resource_id,detail) VALUES(?,'REPORT_EXPORTED',?,'REPORT',?,?)")
                    .params(id, currentUser.username(), id,
                            mapper.writeValueAsString(Map.of("format", format.name(), "encoding", StandardCharsets.UTF_8.name())))
                    .update();
        } catch (Exception ignored) {
            // A completed export remains downloadable even if non-critical audit metadata cannot be serialized.
        }
    }

    public record ExportFile(String fileName, String contentType, byte[] content) {}

    private enum ExportFormat {
        PDF("pdf", "application/pdf"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

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
                throw new BusinessException("UNSUPPORTED_REPORT_FORMAT", "不支持的报告格式，可选：pdf、docx",
                        HttpStatus.BAD_REQUEST);
            }
        }
    }
}
