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

    public String callAiReview(String paperContent, String lang) {
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
        return callAiWithPrompt(systemPrompt, paperContent);
    }

    public String callAiSummary(String paperContent, String lang) {
        String systemPrompt;
        if ("en".equals(lang)) {
            systemPrompt = "You are an academic reading assistant. Read the paper and output a concise HTML reading brief (no markdown code block). " +
                    "Use this structure: " +
                    "<div class='ai-review-card'>" +
                    "<h4>🧠 AI Reading Brief</h4>" +
                    "<p><b>Research focus:</b> ...</p>" +
                    "<p><b>Core contribution:</b> ...</p>" +
                    "<p><b>Method / evidence:</b> ...</p>" +
                    "<p><b>Suggested takeaway:</b> ...</p>" +
                    "</div>. Keep it specific and readable.";
        } else {
            systemPrompt = "你是一名学术阅读助手。请阅读论文，并输出简洁的 HTML 导读卡片（不要加 markdown 代码块）。" +
                    "请严格使用如下结构：" +
                    "<div class='ai-review-card'>" +
                    "<h4>🧠 AI 导读摘要</h4>" +
                    "<p><b>研究主题:</b> ...</p>" +
                    "<p><b>核心贡献:</b> ...</p>" +
                    "<p><b>方法 / 证据:</b> ...</p>" +
                    "<p><b>阅读建议:</b> ...</p>" +
                    "</div>。内容要具体、清晰，适合读者快速理解。";
        }
        return callAiWithPrompt(systemPrompt, paperContent);
    }

    public String callAiDiscussionGuide(String paperContent, String lang) {
        String systemPrompt;
        if ("en".equals(lang)) {
            systemPrompt = "You are an academic discussion facilitator. Read the paper and output an HTML discussion guide (no markdown code block). " +
                    "Use this structure: " +
                    "<div class='ai-review-card'>" +
                    "<h4>💬 AI Discussion Guide</h4>" +
                    "<p><b>Question 1:</b> ...</p>" +
                    "<p><b>Question 2:</b> ...</p>" +
                    "<p><b>Suggestion angle:</b> ...</p>" +
                    "<p><b>Potential limitation:</b> ...</p>" +
                    "</div>. Make the questions useful for a research discussion thread.";
        } else {
            systemPrompt = "你是一名学术讨论引导助手。请阅读论文，并输出 HTML 讨论引导卡片（不要加 markdown 代码块）。" +
                    "请使用如下结构：" +
                    "<div class='ai-review-card'>" +
                    "<h4>💬 AI 讨论引导</h4>" +
                    "<p><b>讨论问题1:</b> ...</p>" +
                    "<p><b>讨论问题2:</b> ...</p>" +
                    "<p><b>建议切入点:</b> ...</p>" +
                    "<p><b>潜在局限:</b> ...</p>" +
                    "</div>。内容要适合直接用于学术讨论区发言。";
        }
        return callAiWithPrompt(systemPrompt, paperContent);
    }

    public String extractKeywords(String title, String content, String lang) {
        String systemPrompt;
        if ("en".equals(lang)) {
            systemPrompt = "You are an academic writing assistant. Extract 5 to 8 concise research keywords from the given title and abstract. " +
                    "Return only a comma-separated list without numbering or explanation.";
        } else {
            systemPrompt = "你是一名学术写作助手。请根据给定的标题和摘要，提炼 5 到 8 个简洁的研究关键词。" +
                    "只返回逗号分隔的关键词，不要编号，不要解释。";
        }
        return callAiWithPrompt(systemPrompt, buildPublishSource(title, content));
    }

    public String polishAbstract(String title, String content, String lang) {
        String systemPrompt;
        if ("en".equals(lang)) {
            systemPrompt = "You are an academic writing assistant. Rewrite and polish the abstract to make it clearer, more formal, and more academic. " +
                    "Keep the original meaning, avoid adding fake claims, and return only the polished abstract text.";
        } else {
            systemPrompt = "你是一名学术写作助手。请润色这段研究摘要，使其表达更清晰、更正式、更符合学术写作风格。" +
                    "保持原意，不要虚构信息，只返回润色后的摘要正文。";
        }
        return callAiWithPrompt(systemPrompt, buildPublishSource(title, content));
    }

    public String parseSearchIntent(String query, String lang) {
        String systemPrompt;
        if ("en".equals(lang)) {
            systemPrompt = "You are an academic search assistant. Analyze the user's natural language search request and convert it into a compact JSON object. " +
                    "Return JSON only, without markdown or explanation. " +
                    "Use this schema: {\"keyword\":\"\",\"researchArea\":\"\",\"contentType\":\"\",\"publicationType\":\"\",\"status\":\"\",\"language\":\"\",\"sort\":\"latest\",\"explanation\":\"\"}. " +
                    "Allowed contentType values: paper, dataset, project, report, code. " +
                    "Allowed publicationType values: journal, conference, preprint, thesis, internal. " +
                    "Allowed status values: draft, ongoing, submitted, published. " +
                    "Allowed sort values: latest, hot, topRated, mostDiscussed, recommended. " +
                    "Allowed language values: 中文, English, 双语, or empty string. " +
                    "If a field cannot be inferred, return an empty string, except sort which defaults to latest.";
        } else {
            systemPrompt = "你是一名学术搜索助手。请理解用户的自然语言搜索请求，并将其转换为紧凑 JSON 对象。" +
                    "只返回 JSON，不要返回 markdown，不要解释。" +
                    "使用如下结构：{\"keyword\":\"\",\"researchArea\":\"\",\"contentType\":\"\",\"publicationType\":\"\",\"status\":\"\",\"language\":\"\",\"sort\":\"latest\",\"explanation\":\"\"}。" +
                    "contentType 可选值：paper、dataset、project、report、code。" +
                    "publicationType 可选值：journal、conference、preprint、thesis、internal。" +
                    "status 可选值：draft、ongoing、submitted、published。" +
                    "sort 可选值：latest、hot、topRated、mostDiscussed、recommended。" +
                    "language 可选值：中文、English、双语，或空字符串。" +
                    "如果无法判断某字段，就返回空字符串；sort 默认 latest。";
        }
        return callAiWithPrompt(systemPrompt, query == null ? "" : query);
    }

    private String buildPublishSource(String title, String content) {
        return "标题:\n" + (title == null ? "" : title) + "\n\n摘要/内容:\n" + (content == null ? "" : content);
    }

    private String callAiWithPrompt(String systemPrompt, String paperContent) {
        RestTemplate restTemplate = new RestTemplate();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("temperature", 1.0);

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
