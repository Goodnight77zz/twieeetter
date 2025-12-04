package com.example.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Service
public class FileService {

    // 对外的主方法：根据后缀名自动选择读取方式
    public String extractTextFromFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return "文件不存在";
        }

        String lowerPath = filePath.toLowerCase();

        try {
            // 1. 如果是 PDF
            if (lowerPath.endsWith(".pdf")) {
                return readPdf(file);
            }
            // 2. 如果是新版 Word (.docx)
            else if (lowerPath.endsWith(".docx")) {
                return readDocx(file);
            }
            // 3. 如果是旧版 Word (.doc)
            else if (lowerPath.endsWith(".doc")) {
                return readDoc(file);
            }
            else {
                return "不支持的文件格式，目前仅支持 PDF, DOC, DOCX";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "文件读取失败: " + e.getMessage();
        }
    }

    // --- 具体的读取逻辑 ---

    private String readPdf(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            // 限制读取前5页，防止太长
            int maxPages = Math.min(document.getNumberOfPages(), 5);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(maxPages);
            return truncate(stripper.getText(document));
        }
    }

    private String readDocx(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return truncate(extractor.getText());
        }
    }

    private String readDoc(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             HWPFDocument doc = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(doc)) {
            return truncate(extractor.getText());
        }
    }

    // 截断文本辅助方法 (防止 AI 内存溢出)
    private String truncate(String text) {
        if (text == null) return "";
        String cleanText = text.trim();
        if (cleanText.length() > 3000) {
            return cleanText.substring(0, 3000) + "...(内容过长已截断)";
        }
        return cleanText;
    }
}