package com.example.backend.service;

import com.example.backend.entity.Tweet;
import com.example.backend.repository.TweetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class RagService {

    private static final int CHUNK_SIZE = 1600;
    private static final int CHUNK_OVERLAP = 200;
    private static final int TOP_K = 5;

    private final TweetRepository tweetRepository;
    private final FileService fileService;
    private final AiService aiService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${rag.enabled:false}")
    private boolean ragEnabled;

    @Value("${rag.db.url}")
    private String ragDbUrl;

    @Value("${rag.db.username}")
    private String ragDbUsername;

    @Value("${rag.db.password}")
    private String ragDbPassword;

    @Value("${rag.embedding.api.url}")
    private String embeddingApiUrl;

    @Value("${rag.embedding.api.key}")
    private String embeddingApiKey;

    @Value("${rag.embedding.model}")
    private String embeddingModel;

    @Value("${rag.embedding.dimension:3072}")
    private int embeddingDimension;

    public RagService(TweetRepository tweetRepository, FileService fileService, AiService aiService) {
        this.tweetRepository = tweetRepository;
        this.fileService = fileService;
        this.aiService = aiService;
    }

    public Map<String, Object> indexTweet(Long tweetId) {
        ensureEnabled();
        Tweet tweet = tweetRepository.findById(tweetId)
                .orElseThrow(() -> new RuntimeException("Tweet not found: " + tweetId));

        String sourceText = buildTweetText(tweet);
        List<String> chunks = splitText(sourceText);
        if (chunks.isEmpty()) {
            return Map.of("tweetId", tweetId, "chunks", 0, "message", "No indexable text found");
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            deleteExistingChunks(connection, tweetId);
            insertChunks(connection, tweetId, chunks);
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to write RAG chunks", e);
        }

        return Map.of("tweetId", tweetId, "chunks", chunks.size(), "message", "RAG index created");
    }

    public Map<String, Object> askTweet(Long tweetId, String question, String lang) {
        ensureEnabled();
        if (question == null || question.trim().isEmpty()) {
            throw new RuntimeException("Question cannot be empty");
        }

        float[] questionEmbedding = createEmbedding(question.trim());
        List<Map<String, Object>> matches = searchChunks(tweetId, questionEmbedding, TOP_K);
        if (matches.isEmpty()) {
            return Map.of(
                    "tweetId", tweetId,
                    "question", question,
                    "answer", "当前成果尚未建立 RAG 索引，请先执行索引接口。",
                    "references", List.of()
            );
        }

        String context = buildContext(matches);
        String answer = aiService.answerWithRagContext(question, context, lang);
        return Map.of(
                "tweetId", tweetId,
                "question", question,
                "answer", answer,
                "references", matches
        );
    }

    private void ensureEnabled() {
        if (!ragEnabled) {
            throw new RuntimeException("RAG is disabled. Set RAG_ENABLED=true to enable it.");
        }
        if (embeddingApiKey == null || embeddingApiKey.isBlank()) {
            throw new RuntimeException("Embedding API key is not configured.");
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(ragDbUrl, ragDbUsername, ragDbPassword);
    }

    private String buildTweetText(Tweet tweet) {
        StringBuilder builder = new StringBuilder();
        appendField(builder, "Title", tweet.getTitle());
        appendField(builder, "Abstract", tweet.getContent());
        appendField(builder, "Authors", tweet.getAuthors());
        appendField(builder, "Institution", tweet.getInstitution());
        appendField(builder, "Research area", tweet.getResearchArea());
        appendField(builder, "Keywords", tweet.getKeywords());
        appendField(builder, "Tags", tweet.getTags());
        appendField(builder, "References", tweet.getReferencesText());

        String filePath = tweet.getFilePath();
        if (filePath != null && !filePath.isBlank()) {
            String fileText = fileService.extractTextFromFile(filePath);
            appendField(builder, "Attachment text", fileText);
        }

        return normalizeText(builder.toString());
    }

    private void appendField(StringBuilder builder, String name, String value) {
        if (value != null && !value.trim().isEmpty()) {
            builder.append(name).append(":\n").append(value.trim()).append("\n\n");
        }
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        String normalized = normalizeText(text);
        if (normalized.isEmpty()) {
            return chunks;
        }

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    private void deleteExistingChunks(Connection connection, Long tweetId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM rag_chunks WHERE tweet_id = ?")) {
            statement.setLong(1, tweetId);
            statement.executeUpdate();
        }
    }

    private void insertChunks(Connection connection, Long tweetId, List<String> chunks) throws SQLException {
        String sql = """
                INSERT INTO rag_chunks (tweet_id, chunk_index, content, embedding)
                VALUES (?, ?, ?, CAST(? AS vector))
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < chunks.size(); i++) {
                float[] embedding = createEmbedding(chunks.get(i));
                statement.setLong(1, tweetId);
                statement.setInt(2, i);
                statement.setString(3, chunks.get(i));
                statement.setString(4, toVectorLiteral(embedding));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<Map<String, Object>> searchChunks(Long tweetId, float[] queryEmbedding, int limit) {
        String vector = toVectorLiteral(queryEmbedding);
        String sql = """
                SELECT chunk_index, content, 1 - (embedding <=> CAST(? AS vector)) AS score
                FROM rag_chunks
                WHERE tweet_id = ?
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """;

        List<Map<String, Object>> matches = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vector);
            statement.setLong(2, tweetId);
            statement.setString(3, vector);
            statement.setInt(4, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("chunkIndex", resultSet.getInt("chunk_index"));
                    item.put("content", resultSet.getString("content"));
                    item.put("score", resultSet.getDouble("score"));
                    matches.add(item);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search RAG chunks", e);
        }
        return matches;
    }

    @SuppressWarnings("unchecked")
    private float[] createEmbedding(String input) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", embeddingModel);
        request.put("input", input == null ? "" : input);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + embeddingApiKey);

        org.springframework.http.HttpEntity<Map<String, Object>> entity =
                new org.springframework.http.HttpEntity<>(request, headers);

        Map<String, Object> response = restTemplate.postForObject(embeddingApiUrl, entity, Map.class);
        if (response == null || !response.containsKey("data")) {
            throw new RuntimeException("Embedding API returned empty response");
        }

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        if (data == null || data.isEmpty() || !data.get(0).containsKey("embedding")) {
            throw new RuntimeException("Embedding API response does not contain embedding");
        }

        List<Object> values = (List<Object>) data.get(0).get("embedding");
        if (values.size() != embeddingDimension) {
            throw new RuntimeException("Embedding dimension mismatch. Expected "
                    + embeddingDimension + ", got " + values.size());
        }

        float[] embedding = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            embedding[i] = ((Number) values.get(i)).floatValue();
        }
        return embedding;
    }

    private String toVectorLiteral(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : embedding) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

    private String buildContext(List<Map<String, Object>> matches) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            Map<String, Object> item = matches.get(i);
            builder.append("Excerpt ").append(i + 1)
                    .append(" (score: ").append(item.get("score")).append(")\n")
                    .append(item.get("content")).append("\n\n");
        }
        return builder.toString();
    }
}
