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

    // 从配置文件读取 URL 和 Key
    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.api.key}")
    private String apiKey;

    public String callAiReview(String paperContent) {
        RestTemplate restTemplate = new RestTemplate();

        // 1. 构造 Prompt (提示词) - 可以根据 DeepSeek 的特点微调
        String systemPrompt = "你是一个资深的学术评审专家。请对用户提供的论文内容进行专业评审。\n" +
                "请严格按照以下格式输出 HTML 代码（不要包含 ```html 标记）：\n" +
                "1. **创新点**：列出3条。\n" +
                "2. **方法论分析**：简要评价技术路线。\n" +
                "3. **改进建议**：给出具体可行的建议。\n" +
                "内容要精炼，语气要客观。如果是中文文献，尽量用中文回答，英文文献，尽量用英文回答";

        // 2. 构造请求体
        Map<String, Object> requestBody = new HashMap<>();
        // 重要：DeepSeek 必须指定模型名称，常用 'deepseek-chat' (V3) 或 'deepseek-reasoner' (R1)
        requestBody.put("model", "deepseek-chat");
        requestBody.put("temperature", 1.3); // DeepSeek 建议 V3 设为 1.3 以获得更丰富的内容

        // 构造消息列表
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", "论文内容如下:\n" + paperContent));

        requestBody.put("messages", messages);
        requestBody.put("stream", false); // 关闭流式输出，一次性返回

        // 3. 构造请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey); // 鉴权头

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            // 发送 POST 请求
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

            // 4. 解析返回结果 (结构和 OpenAI 一样)
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
            return "AI 服务调用失败: " + e.getMessage(); // 在前端显示报错信息
        }
    }
}