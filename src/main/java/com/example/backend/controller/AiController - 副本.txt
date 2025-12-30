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

    // 🔥 修改点：增加了 lang 参数，默认值为 zh
    @PostMapping("/evaluate/{tweetId}")
    public Map<String, String> evaluatePaper(
            @PathVariable Long tweetId,
            @RequestParam(defaultValue = "zh") String lang
    ) {
        Tweet tweet = tweetRepository.findById(tweetId)
                .orElseThrow(() -> new RuntimeException("未找到该研究记录"));

        String filePath = tweet.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            return Map.of("result", "该研究没有上传附件，AI 无法评审。");
        }

        String extractedText = fileService.extractTextFromFile(filePath);

        // 🔥 修改点：把 lang 传给 Service
        String aiResponse = aiService.callAiReview(extractedText, lang);

        return Map.of("result", aiResponse);
    }
}