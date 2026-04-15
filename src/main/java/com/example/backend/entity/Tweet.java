package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "tweets")
@Data
public class Tweet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String title;

    @Column(length = 2000)
    private String content;

    private String authors;
    private String institution;
    private String researchArea;
    private String keywords;
    private String contentType;
    private String publicationType;
    private String status;
    private String journalOrConference;
    private String publicationDate;
    private String doi;
    private String language;
    private String visibility;

    @Column(length = 2000)
    private String referencesText;

    @Column(length = 1000)
    private String projectLinks;

    private String filePath;
    private String originalFilename;

    @Column(name = "tags")
    private String tags;

    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "last_interaction_time")
    private LocalDateTime lastInteractionTime;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User author;

    @Column(name = "download_count", columnDefinition = "bigint default 0")
    private Long downloadCount = 0L;

    @Column(name = "view_count", columnDefinition = "bigint default 0")
    private Long viewCount = 0L;

    @Column(name = "share_count", columnDefinition = "bigint default 0")
    private Long shareCount = 0L;

    @Column(name = "bookmark_count", columnDefinition = "bigint default 0")
    private Long bookmarkCount = 0L;
}
