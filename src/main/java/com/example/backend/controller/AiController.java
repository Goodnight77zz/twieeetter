package com.example.backend.controller;

import com.example.backend.entity.Tweet;
import com.example.backend.repository.TweetRepository;
import com.example.backend.service.AiService;
import com.example.backend.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private TweetRepository tweetRepository;
    @Autowired
    private FileService fileService;
    @Autowired
    private AiService aiService;

    @PostMapping("/evaluate/{tweetId}")
    public Map<String, String> evaluatePaper(@PathVariable Long tweetId) {
        // 1. 查数据库找到推文信息
        Tweet tweet = tweetRepository.findById(tweetId)
                .orElseThrow(() -> new RuntimeException("未找到该研究记录"));

        String filePath = tweet.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            return Map.of("result", "该研究没有上传附件，AI 无法评审。");
        }

        // 2. 提取 PDF 文本
        String extractedText = fileService.extractTextFromFile(filePath);

        // 3. 调用 AI 分析
        String aiResponse = aiService.callAiReview(extractedText);

        return Map.of("result", aiResponse);
    }
}