package com.example.backend.service;

import com.example.backend.entity.Tweet;
import com.example.backend.entity.User;
import com.example.backend.repository.TweetRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TweetService {

    @Autowired private TweetRepository tweetRepository;
    @Autowired private UserRepository userRepository;

    @Value("${file.upload.dir}")
    private String uploadDir;

    // === 🔥 修改：增加了 tags 参数 ===
    public Tweet postTweetWithFile(String content, Long userId, String tags, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Tweet tweet = new Tweet();
        tweet.setContent(content);
        tweet.setAuthor(user);
        tweet.setTags(tags); // 保存标签到数据库
        tweet.setCreateTime(LocalDateTime.now());

        if (file != null && !file.isEmpty()) {
            String uuid = UUID.randomUUID().toString();
            String newFileName = uuid + "_" + file.getOriginalFilename();

            File destFile = new File(uploadDir + newFileName);
            if (!destFile.getParentFile().exists()) {
                destFile.getParentFile().mkdirs();
            }
            file.transferTo(destFile);

            tweet.setFilePath(destFile.getAbsolutePath());
            tweet.setOriginalFilename(file.getOriginalFilename());
        }
        return tweetRepository.save(tweet);
    }

    public List<Tweet> getAllTweets() {
        return tweetRepository.findAllByOrderByCreateTimeDesc();
    }

    // === 搜索逻辑 ===
    public List<Tweet> searchTweets(String keyword) {
        // 调用 Repository 的搜索方法
        return tweetRepository.findByContentContainingOrTagsContainingOrderByCreateTimeDesc(keyword, keyword);
    }
}