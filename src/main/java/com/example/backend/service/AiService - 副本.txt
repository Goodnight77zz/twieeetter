package com.example.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.api.key}")
    private String apiKey;

    // 🔥 修改点：增加 lang 参数
    public String callAiReview(String paperContent, String lang) {
        RestTemplate restTemplate = new RestTemplate();

        // 🔥 根据语言生成不同的提示词
        String systemPrompt;


        if ("en".equals(lang)) {
            systemPrompt = "You are a harsh academic reviewer. Analyze the paper and output a review in strict HTML format (no ```html tags):\n" +
                    "<div class='ai-review-card'>" +
                    "  <div class='ai-score-box'>" +
                    "    <div>Innovation: <b>{score}/5</b></div>" +
                    "    <div>Methodology: <b>{score}/5</b></div>" +
                    "    <div>Utility: <b>{score}/5</b></div>" +
                    "  </div>" +
                    "  <h4>🤖 AI Initial Review Report</h4>" +
                    "  <p><b>Summary:</b> ...</p>" +
                    "  <p><b>Critique:</b> ...</p>" +
                    "</div>" +
                    "Fill in specific scores (1-5) and content based on the paper.";
        } else {
            systemPrompt = "你是一位严格的学术期刊审稿人。请阅读论文，并严格按以下 HTML 格式输出评审报告（不要加 ```html 标签）：\n" +
                    "<div class='ai-review-card'>" +
                    "  <div class='ai-score-box'>" +
                    "    <div>💡 创新性: <b>{分数}/5</b></div>" +
                    "    <div>📐 方法论: <b>{分数}/5</b></div>" +
                    "    <div>🛠️ 实用性: <b>{分数}/5</b></div>" +
                    "  </div>" +
                    "  <h4>🤖 AI 初审报告</h4>" +
                    "  <p><b>核心摘要:</b> (请总结论文核心贡献)</p>" +
                    "  <p><b>评审意见:</b> (请指出优点和不足)</p>" +
                    "</div>" +
                    "请根据论文质量填入具体分数（1-5分）和内容。";
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("temperature", 1.3);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", "论文全文内容如下:\n" + paperContent));

        requestBody.put("messages", messages);
        requestBody.put("stream", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            return "AI 未返回有效内容";

        } catch (Exception e) {
            e.printStackTrace();
            return "AI 服务调用失败: " + e.getMessage();
        }
    }
}