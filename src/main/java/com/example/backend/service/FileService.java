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

    public String extractTextFromFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return "文件不存在";
        }

        String lowerPath = filePath.toLowerCase();

        try {
            if (lowerPath.endsWith(".pdf")) {
                return readPdf(file);
            } else if (lowerPath.endsWith(".docx")) {
                return readDocx(file);
            } else if (lowerPath.endsWith(".doc")) {
                return readDoc(file);
            } else {
                return "不支持的文件格式，目前仅支持 PDF, DOC, DOCX";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "文件读取失败: " + e.getMessage();
        }
    }

    // === 读取 PDF (无限制版) ===
    private String readPdf(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            // 🔥 修改点 1：删除了 maxPages 限制，读取所有页
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            // 默认就是从第1页读到最后一页
            return cleanText(stripper.getText(document));
        }
    }

    // === 读取 Word .docx (无限制版) ===
    private String readDocx(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return cleanText(extractor.getText());
        }
    }

    // === 读取 Word .doc (无限制版) ===
    private String readDoc(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             HWPFDocument doc = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(doc)) {
            return cleanText(extractor.getText());
        }
    }

    // === 🔥 修改点 2：改名为 cleanText，不再截断字数 ===
    private String cleanText(String text) {
        if (text == null) return "";
        // 简单清洗：去掉多余的空白字符，节省 Token
        return text.trim();

        // ⚠️ 注意：DeepSeek V3 最大支持约 60000 字符。
        // 如果你的论文特别长（比如博士论文），可能还是需要截断，
        // 但对于普通 10-20 页的论文，这里不需要限制了。
        /*
        if (cleanText.length() > 60000) {
            return cleanText.substring(0, 60000) + "...(超长截断)";
        }
        return cleanText;
        */
    }
}