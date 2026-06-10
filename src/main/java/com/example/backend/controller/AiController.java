package com.example.backend.controller;

import com.example.backend.entity.Tweet;
import com.example.backend.repository.TweetRepository;
import com.example.backend.service.AiService;
import com.example.backend.service.FileService;
import com.example.backend.service.RagService;
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
    @Autowired
    private RagService ragService;

    @PostMapping("/{task}/{tweetId}")
    public Map<String, String> runAiTask(
            @PathVariable String task,
            @PathVariable Long tweetId,
            @RequestParam(defaultValue = "zh") String lang
    ) {
        Tweet tweet = tweetRepository.findById(tweetId)
                .orElseThrow(() -> new RuntimeException("未找到该研究记录"));

        String filePath = tweet.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            return Map.of("result", "该研究没有上传附件，AI 无法分析。", "task", task);
        }

        String extractedText = fileService.extractTextFromFile(filePath);
        String aiResponse;
        switch (task.toLowerCase()) {
            case "summary":
                aiResponse = aiService.callAiSummary(extractedText, lang);
                break;
            case "discussion":
                aiResponse = aiService.callAiDiscussionGuide(extractedText, lang);
                break;
            case "review":
            case "evaluate":
            default:
                aiResponse = aiService.callAiReview(extractedText, lang);
                break;
        }

        return Map.of("result", aiResponse, "task", task);
    }

    @PostMapping("/publish-helper")
    public Map<String, String> runPublishHelper(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam String task,
            @RequestParam(defaultValue = "zh") String lang
    ) {
        String aiResponse;
        switch (task.toLowerCase()) {
            case "keywords":
                aiResponse = aiService.extractKeywords(title, content, lang);
                break;
            case "polish":
                aiResponse = aiService.polishAbstract(title, content, lang);
                break;
            default:
                aiResponse = "Unsupported publish helper task";
                break;
        }
        return Map.of("result", aiResponse, "task", task);
    }

    @PostMapping("/search-helper")
    public Map<String, String> runSearchHelper(
            @RequestParam String query,
            @RequestParam(defaultValue = "zh") String lang
    ) {
        String aiResponse = aiService.parseSearchIntent(query, lang);
        return Map.of("result", aiResponse, "task", "search-helper");
    }

    @PostMapping("/rag/index/{tweetId}")
    public Map<String, Object> indexTweetForRag(@PathVariable Long tweetId) {
        return ragService.indexTweet(tweetId);
    }

    @PostMapping("/rag/index-all")
    public Map<String, Object> indexAllTweetsForRag() {
        return ragService.indexAllTweets();
    }

    @PostMapping("/rag/ask/{tweetId}")
    public Map<String, Object> askTweetWithRag(
            @PathVariable Long tweetId,
            @RequestParam String question,
            @RequestParam(defaultValue = "zh") String lang
    ) {
        return ragService.askTweet(tweetId, question, lang);
    }

    @GetMapping("/rag/semantic-related/{tweetId}")
    public Map<String, Object> getSemanticRelatedTweets(@PathVariable Long tweetId) {
        return ragService.recommendSimilarTweets(tweetId);
    }
}
