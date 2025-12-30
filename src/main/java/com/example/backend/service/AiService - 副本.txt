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
            // === 英文提示词 ===
            systemPrompt = "You are a senior academic reviewer. Users will provide a full academic paper.\n" +
                    "Please read the full text and output a review report strictly in the following HTML format (do not include ```html tags):\n\n" +
                    "<h3>1. Core Contributions</h3>\n" +
                    "<p>Summarize the problems solved and the main innovations (list 3 points).</p>\n" +
                    "<h3>2. Methodology Evaluation</h3>\n" +
                    "<p>Analyze the rationality of the technical route and point out pros and cons.</p>\n" +
                    "<h3>3. Improvements</h3>\n" +
                    "<p>Give specific suggestions for the shortcomings.</p>\n\n" +
                    "Note: The output must be professional, objective, and **MUST BE IN ENGLISH**.";
        } else {
            // === 中文提示词 ===
            systemPrompt = "你是一个资深的学术评审专家。用户将提供一篇完整的学术论文内容。\n" +
                    "请阅读全文，并严格按照以下 HTML 格式输出评审报告（不要包含 ```html 标记）：\n\n" +
                    "<h3>1. 核心贡献 (Core Contributions)</h3>\n" +
                    "<p>总结论文解决了什么问题，以及主要的创新点（列出3点）。</p>\n" +
                    "<h3>2. 方法论评估 (Methodology)</h3>\n" +
                    "<p>分析其技术路线的合理性，指出优缺点。</p>\n" +
                    "<h3>3. 改进建议 (Improvements)</h3>\n" +
                    "<p>针对不足之处给出具体建议。</p>\n\n" +
                    "注意：输出内容要专业、客观，**请必须使用中文进行评审**。";
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