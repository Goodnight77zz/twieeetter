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

    @Autowired
    private TweetRepository tweetRepository;

    @Autowired
    private UserRepository userRepository;

    // 读取配置文件里的 file.upload.dir
    @Value("${file.upload.dir}")
    private String uploadDir;

    // 发布带论文的推文
    public Tweet postTweetWithFile(String content, Long userId, MultipartFile file) throws IOException {
        // 1. 找用户
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 2. 准备推文对象
        Tweet tweet = new Tweet();
        tweet.setContent(content);
        tweet.setAuthor(user);
        tweet.setCreateTime(LocalDateTime.now());

        // 3. 处理文件上传逻辑
        if (file != null && !file.isEmpty()) {
            // 获取原始文件名 (例如: my_paper.pdf)
            String originalFilename = file.getOriginalFilename();

            // 生成一个唯一的文件名，防止文件名冲突 (例如: uuid_my_paper.pdf)
            String uuid = UUID.randomUUID().toString();
            String newFileName = uuid + "_" + originalFilename;

            // 创建目标文件对象
            File destFile = new File(uploadDir + newFileName);

            // 确保文件夹存在
            if (!destFile.getParentFile().exists()) {
                destFile.getParentFile().mkdirs();
            }

            // **核心动作：把文件写入硬盘**
            file.transferTo(destFile);

            // 把路径保存到数据库对象里
            tweet.setFilePath(destFile.getAbsolutePath());
            tweet.setOriginalFilename(originalFilename);
        }

        // 4. 保存到数据库
        return tweetRepository.save(tweet);
    }

    // 获取列表
    public List<Tweet> getAllTweets() {
        return tweetRepository.findAllByOrderByCreateTimeDesc();
    }
}