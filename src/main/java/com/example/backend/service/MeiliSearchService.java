package com.example.backend.service;

import com.example.backend.entity.Tweet;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.Searchable;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                    "title", "content", "keywords", "tags", "authors", "institution", "researchArea", "journalOrConference", "doi", "language"
            });
            index.updateFilterableAttributesSettings(new String[]{
                    "researchArea", "contentType", "publicationType", "status", "language", "authorId"
            });
            index.updateSortableAttributesSettings(new String[]{
                    "createTimeMillis", "updateTimeMillis"
            });
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
