package com.example.backend.service;

import com.example.backend.entity.User;
import com.example.backend.entity.Friendship;
import com.example.backend.repository.UserRepository;
import com.example.backend.repository.FriendshipRepository;
import com.example.backend.repository.TweetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.stream.Collectors;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private static final String DEFAULT_TEST_EMAIL = "1747607922@qq.com";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private TweetRepository tweetRepository;

    @Value("${file.upload.dir}")
    private String uploadDir;

    @CacheEvict(cacheNames = "users:profile", key = "#result.id", condition = "#result != null && #result.id != null")
    public User register(User user) {
        if (userRepository.findByUsername(user.getUsername()) != null) {
            throw new RuntimeException("用户名已存在");
        }
        if (user.getNickname() == null || user.getNickname().isEmpty()) {
            user.setNickname(user.getUsername());
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            user.setEmail(DEFAULT_TEST_EMAIL);
        }
        return userRepository.save(user);
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user != null && user.getPassword().equals(password)) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                user.setEmail(DEFAULT_TEST_EMAIL);
                userRepository.save(user);
            }
            return user;
        }
        return null;
    }

    @Cacheable(cacheNames = "users:profile", key = "#id")
    public User getUserById(Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null && (user.getEmail() == null || user.getEmail().isBlank())) {
            user.setEmail(DEFAULT_TEST_EMAIL);
            return userRepository.save(user);
        }
        return user;
    }

    @Cacheable(cacheNames = "users:stats", key = "#userId")
    public Map<String, Long> getUserStats(Long userId) {
        long authoredTweetCount = tweetRepository.countByAuthorId(userId);
        long followingCount = userRepository.countFollowing(userId);
        long followerCount = userRepository.countFollowers(userId);

        return Map.of(
                "tweetCount", authoredTweetCount,
                "followingCount", followingCount,
                "followerCount", followerCount
        );
    }

    @Cacheable(cacheNames = "users:following", key = "#userId")
    public List<User> getMyFollowing(Long userId) {
        List<Friendship> friendships = friendshipRepository.findAllByFollowerId(userId);
        return friendships.stream()
                .map(Friendship::getFollowing)
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "users:follow-status", key = "#userId + ':' + #targetUserId")
    public boolean isFollowing(Long userId, Long targetUserId) {
        return friendshipRepository.existsByFollowerIdAndFollowingId(userId, targetUserId);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "users:profile", key = "#userId"),
            @CacheEvict(cacheNames = "users:archive", key = "#userId"),
            @CacheEvict(cacheNames = "users:interest", key = "#userId")
    })
    public User updateProfile(Long userId, String nickname, String bio, MultipartFile avatarFile) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (bio != null) user.setBio(bio);
        if (nickname != null && !nickname.trim().isEmpty()) user.setNickname(nickname);

        if (avatarFile != null && !avatarFile.isEmpty()) {
            String newFileName = UUID.randomUUID().toString() + "_" + avatarFile.getOriginalFilename();
            File dest = new File(uploadDir + newFileName);
            if (!dest.getParentFile().exists()) dest.getParentFile().mkdirs();
            avatarFile.transferTo(dest);
            user.setAvatar(newFileName);
        }
        return userRepository.save(user);
    }

    public List<User> searchUsers(String keyword) {
        return userRepository.findByUsernameContainingOrNicknameContaining(keyword, keyword);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "users:profile", key = "#followerId"),
            @CacheEvict(cacheNames = "users:profile", key = "#targetUserId"),
            @CacheEvict(cacheNames = "users:following", key = "#followerId"),
            @CacheEvict(cacheNames = "users:follow-status", key = "#followerId + ':' + #targetUserId"),
            @CacheEvict(cacheNames = "users:stats", key = "#followerId"),
            @CacheEvict(cacheNames = "users:stats", key = "#targetUserId"),
            @CacheEvict(cacheNames = "users:interest", key = "#followerId")
    })
    public void followUser(Long followerId, Long targetUserId) {
        if (followerId.equals(targetUserId)) {
            throw new RuntimeException("不能关注自己");
        }
        if (friendshipRepository.existsByFollowerIdAndFollowingId(followerId, targetUserId)) {
            throw new RuntimeException("已经关注过了");
        }

        User follower = userRepository.findById(followerId).orElseThrow();
        User target = userRepository.findById(targetUserId).orElseThrow(() -> new RuntimeException("目标用户不存在"));

        Friendship friendship = new Friendship();
        friendship.setFollower(follower);
        friendship.setFollowing(target);
        friendship.setCreateTime(LocalDateTime.now());

        friendshipRepository.save(friendship);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "users:profile", key = "#followerId"),
            @CacheEvict(cacheNames = "users:profile", key = "#targetUserId"),
            @CacheEvict(cacheNames = "users:following", key = "#followerId"),
            @CacheEvict(cacheNames = "users:follow-status", key = "#followerId + ':' + #targetUserId"),
            @CacheEvict(cacheNames = "users:stats", key = "#followerId"),
            @CacheEvict(cacheNames = "users:stats", key = "#targetUserId"),
            @CacheEvict(cacheNames = "users:interest", key = "#followerId")
    })
    public void unfollowUser(Long followerId, Long targetUserId) {
        Friendship friendship = friendshipRepository.findByFollowerIdAndFollowingId(followerId, targetUserId)
                .orElseThrow(() -> new RuntimeException("未关注该用户"));
        friendshipRepository.delete(friendship);
    }

}
