package com.example.backend.service;

import com.example.backend.entity.Tweet;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.Searchable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class MeiliSearchService {

    private final boolean enabled;
    private final String indexUid;
    private final Client client;

    public MeiliSearchService(
            @Value("${meilisearch.enabled:false}") boolean enabled,
            @Value("${meilisearch.host:http://127.0.0.1:7700}") String host,
            @Value("${meilisearch.api-key:}") String apiKey,
            @Value("${meilisearch.index:tweets}") String indexUid
    ) {
        this.enabled = enabled;
        this.indexUid = indexUid;
        this.client = enabled ? new Client(new Config(host, apiKey)) : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void ensureIndex() {
        if (!enabled) {
            return;
        }
        try {
            Index index;
            try {
                index = client.getIndex(indexUid);
            } catch (MeilisearchException e) {
                client.createIndex(indexUid, "id");
                index = client.getIndex(indexUid);
            }
            index.updateSearchableAttributesSettings(new String[]{
                    "title",
                    "keywords",
                    "tags",
                    "researchArea",
                    "authors",
                    "institution",
                    "journalOrConference",
                    "doi",
                    "content",
                    "language",
                    "contentType",
                    "publicationType",
                    "status"
            });
            index.updateDisplayedAttributesSettings(new String[]{
                    "id",
                    "title",
                    "content",
                    "keywords",
                    "tags",
                    "authors",
                    "institution",
                    "researchArea",
                    "contentType",
                    "publicationType",
                    "status",
                    "journalOrConference",
                    "doi",
                    "language",
                    "authorId",
                    "createTimeMillis",
                    "updateTimeMillis"
            });
            index.updateFilterableAttributesSettings(new String[]{
                    "researchArea", "contentType", "publicationType", "status", "language", "authorId"
            });
            index.updateSortableAttributesSettings(new String[]{
                    "createTimeMillis", "updateTimeMillis"
            });
            index.updateRankingRulesSettings(new String[]{
                    "words",
                    "typo",
                    "proximity",
                    "attribute",
                    "sort",
                    "exactness"
            });
            index.updateStopWordsSettings(new String[]{
                    "a", "an", "the", "and", "or", "of", "for", "to", "in", "on", "with", "by",
                    "from", "at", "is", "are", "was", "were", "be", "been", "this", "that", "these", "those",
                    "study", "paper", "research", "article", "method", "approach", "based"
            });
            index.updateDistinctAttributeSettings("id");
            index.updateSynonymsSettings(buildAcademicSynonyms());
        } catch (Exception ignored) {
        }
    }

    public void indexTweet(Tweet tweet) {
        if (!enabled || tweet == null || tweet.getId() == null) {
            return;
        }
        try {
            ensureIndex();
            Index index = client.getIndex(indexUid);
            Map<String, Object> document = toDocument(tweet);
            index.addDocuments(toJson(List.of(document)), "id");
        } catch (Exception ignored) {
        }
    }

    public void indexAll(List<Tweet> tweets) {
        if (!enabled || tweets == null || tweets.isEmpty()) {
            return;
        }
        try {
            ensureIndex();
            List<Map<String, Object>> documents = tweets.stream()
                    .filter(Objects::nonNull)
                    .map(this::toDocument)
                    .toList();
            client.getIndex(indexUid).addDocuments(toJson(documents), "id");
        } catch (Exception ignored) {
        }
    }

    public List<Long> searchTweetIds(String keyword,
                                     String researchArea,
                                     String contentType,
                                     String publicationType,
                                     String status,
                                     String language,
                                     int limit) {
        if (!enabled) {
            return List.of();
        }
        try {
            ensureIndex();
            SearchRequest request = new SearchRequest(keyword == null ? "" : keyword);
            request.setLimit(Math.max(limit, 1));
            String filter = buildFilter(researchArea, contentType, publicationType, status, language);
            if (!filter.isBlank()) {
                request.setFilter(new String[]{filter});
            }
            Searchable searchable = client.getIndex(indexUid).search(request);
            List<Long> ids = new ArrayList<>();
            for (Map<String, Object> hit : searchable.getHits()) {
                Object id = hit.get("id");
                if (id instanceof Number number) {
                    ids.add(number.longValue());
                } else if (id != null) {
                    ids.add(Long.parseLong(String.valueOf(id)));
                }
            }
            return ids;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildFilter(String researchArea,
                               String contentType,
                               String publicationType,
                               String status,
                               String language) {
        List<String> filters = new ArrayList<>();
        addFilter(filters, "researchArea", researchArea);
        addFilter(filters, "contentType", contentType);
        addFilter(filters, "publicationType", publicationType);
        addFilter(filters, "status", status);
        addFilter(filters, "language", language);
        return String.join(" AND ", filters);
    }

    private void addFilter(List<String> filters, String field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        filters.add(field + " = \"" + value.replace("\"", "\\\"") + "\"");
    }

    private Map<String, Object> toDocument(Tweet tweet) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", tweet.getId());
        doc.put("title", safe(tweet.getTitle()));
        doc.put("content", safe(tweet.getContent()));
        doc.put("keywords", safe(tweet.getKeywords()));
        doc.put("tags", safe(tweet.getTags()));
        doc.put("authors", safe(tweet.getAuthors()));
        doc.put("institution", safe(tweet.getInstitution()));
        doc.put("researchArea", safe(tweet.getResearchArea()));
        doc.put("contentType", safe(tweet.getContentType()));
        doc.put("publicationType", safe(tweet.getPublicationType()));
        doc.put("status", safe(tweet.getStatus()));
        doc.put("journalOrConference", safe(tweet.getJournalOrConference()));
        doc.put("doi", safe(tweet.getDoi()));
        doc.put("language", safe(tweet.getLanguage()));
        doc.put("authorId", tweet.getAuthor() != null ? tweet.getAuthor().getId() : null);
        doc.put("createTimeMillis", tweet.getCreateTime() != null ? tweet.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0L);
        doc.put("updateTimeMillis", tweet.getUpdateTime() != null ? tweet.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0L);
        return doc;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Map<String, String[]> buildAcademicSynonyms() {
        Map<String, String[]> synonyms = new LinkedHashMap<>();
        addSynonymGroup(synonyms, "ai", "artificial intelligence", "人工智能", "智能系统", "intelligent system");
        addSynonymGroup(synonyms, "machine learning", "ml", "机器学习", "统计学习");
        addSynonymGroup(synonyms, "deep learning", "dl", "深度学习", "神经网络", "neural network", "neural networks");
        addSynonymGroup(synonyms, "llm", "large language model", "large language models", "大语言模型", "语言大模型");
        addSynonymGroup(synonyms, "nlp", "natural language processing", "自然语言处理", "文本处理", "文本理解");
        addSynonymGroup(synonyms, "computer vision", "cv", "计算机视觉", "视觉识别", "图像识别", "image recognition");
        addSynonymGroup(synonyms, "multimodal", "multi-modal", "多模态", "跨模态");
        addSynonymGroup(synonyms, "recommendation system", "recommender system", "推荐系统", "个性化推荐", "recommendation");
        addSynonymGroup(synonyms, "information retrieval", "retrieval", "信息检索", "搜索", "search engine", "搜索引擎");
        addSynonymGroup(synonyms, "knowledge graph", "kg", "知识图谱", "知识网络");
        addSynonymGroup(synonyms, "data mining", "数据挖掘", "模式发现");
        addSynonymGroup(synonyms, "big data", "大数据", "海量数据");
        addSynonymGroup(synonyms, "database", "db", "数据库", "data management", "数据管理");
        addSynonymGroup(synonyms, "data science", "数据科学", "数据分析", "analytics");
        addSynonymGroup(synonyms, "cybersecurity", "security", "网络安全", "信息安全", "安全防护");
        addSynonymGroup(synonyms, "privacy", "隐私", "数据隐私", "privacy protection", "隐私保护");
        addSynonymGroup(synonyms, "blockchain", "区块链", "distributed ledger", "分布式账本");
        addSynonymGroup(synonyms, "internet of things", "iot", "物联网", "智能感知");
        addSynonymGroup(synonyms, "edge computing", "边缘计算", "端侧计算");
        addSynonymGroup(synonyms, "cloud computing", "云计算", "distributed computing", "分布式计算");
        addSynonymGroup(synonyms, "high performance computing", "hpc", "高性能计算", "并行计算");
        addSynonymGroup(synonyms, "human computer interaction", "hci", "人机交互", "交互设计");
        addSynonymGroup(synonyms, "software engineering", "软件工程", "software development", "软件开发");
        addSynonymGroup(synonyms, "program analysis", "程序分析", "code analysis", "代码分析");
        addSynonymGroup(synonyms, "formal methods", "形式化方法", "formal verification", "形式化验证");
        addSynonymGroup(synonyms, "robotics", "robot", "机器人", "智能机器人");
        addSynonymGroup(synonyms, "autonomous driving", "self-driving", "无人驾驶", "自动驾驶");
        addSynonymGroup(synonyms, "bioinformatics", "生物信息学", "computational biology", "计算生物学");
        addSynonymGroup(synonyms, "medical imaging", "医学影像", "影像分析", "medical image analysis");
        addSynonymGroup(synonyms, "healthcare", "digital health", "医疗健康", "智慧医疗");
        addSynonymGroup(synonyms, "education technology", "edtech", "教育技术", "智能教育");
        addSynonymGroup(synonyms, "fintech", "financial technology", "金融科技", "智能金融");
        addSynonymGroup(synonyms, "smart city", "智慧城市", "urban computing", "城市计算");
        addSynonymGroup(synonyms, "social network", "社交网络", "网络社区", "online community");
        addSynonymGroup(synonyms, "sentiment analysis", "情感分析", "opinion mining", "观点挖掘");
        addSynonymGroup(synonyms, "text classification", "文本分类", "document classification", "文档分类");
        addSynonymGroup(synonyms, "text summarization", "文本摘要", "自动摘要", "summarization");
        addSynonymGroup(synonyms, "question answering", "qa", "问答系统", "智能问答");
        addSynonymGroup(synonyms, "speech recognition", "asr", "语音识别", "automatic speech recognition");
        addSynonymGroup(synonyms, "speech synthesis", "tts", "语音合成", "text to speech");
        addSynonymGroup(synonyms, "translation", "machine translation", "翻译", "机器翻译");
        addSynonymGroup(synonyms, "graph neural network", "gnn", "图神经网络", "graph learning");
        addSynonymGroup(synonyms, "transformer", "attention model", "注意力模型");
        addSynonymGroup(synonyms, "convolutional neural network", "cnn", "卷积神经网络");
        addSynonymGroup(synonyms, "recurrent neural network", "rnn", "循环神经网络");
        addSynonymGroup(synonyms, "reinforcement learning", "rl", "强化学习", "策略学习");
        addSynonymGroup(synonyms, "federated learning", "联邦学习", "privacy preserving learning", "隐私保护学习");
        addSynonymGroup(synonyms, "anomaly detection", "异常检测", "outlier detection", "离群检测");
        addSynonymGroup(synonyms, "time series", "时间序列", "时序预测", "sequence forecasting");
        addSynonymGroup(synonyms, "optimization", "最优化", "优化算法", "optimisation");
        addSynonymGroup(synonyms, "simulation", "仿真", "建模仿真", "modeling");
        addSynonymGroup(synonyms, "dataset", "data set", "数据集", "corpus", "语料库");
        addSynonymGroup(synonyms, "paper", "article", "论文", "研究论文", "学术论文");
        addSynonymGroup(synonyms, "preprint", "预印本", "working paper");
        addSynonymGroup(synonyms, "thesis", "dissertation", "学位论文", "毕业论文");
        addSynonymGroup(synonyms, "code", "source code", "源码", "代码仓库");
        addSynonymGroup(synonyms, "benchmark", "基准测试", "评测集", "evaluation benchmark");
        return synonyms;
    }

    private void addSynonymGroup(Map<String, String[]> synonyms, String... terms) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String term : terms) {
            if (term != null && !term.isBlank()) {
                normalized.add(term.trim());
            }
        }
        if (normalized.size() < 2) {
            return;
        }
        List<String> allTerms = new ArrayList<>(normalized);
        for (String term : allTerms) {
            List<String> related = new ArrayList<>();
            for (String candidate : allTerms) {
                if (!candidate.equals(term)) {
                    related.add(candidate);
                }
            }
            synonyms.put(term, related.toArray(new String[0]));
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            return mapToJson(map);
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(toJson(list.get(i)));
            }
            builder.append(']');
            return builder.toString();
        }
        if (value instanceof String[] array) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < array.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(toJson(array[i]));
            }
            builder.append(']');
            return builder.toString();
        }
        if (value instanceof String stringValue) {
            return '"' + escapeJson(stringValue) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return '"' + escapeJson(String.valueOf(value)) + '"';
    }

    private String mapToJson(Map<?, ?> map) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"')
                    .append(escapeJson(String.valueOf(entry.getKey())))
                    .append('"')
                    .append(':')
                    .append(toJson(entry.getValue()));
        }
        builder.append('}');
        return builder.toString();
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
