package com.example.backend.repository;

import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);

    // 模糊搜索 (只要用户名 OR 昵称包含关键词，就找出来)
    List<User> findByUsernameContainingOrNicknameContaining(String keyword1, String keyword2);

    // 统计我有多少粉丝 (我是 following_id)
    @Query("SELECT COUNT(f) FROM Friendship f WHERE f.following.id = :userId")
    long countFollowers(Long userId);

    // 统计我关注了多少人 (我是 follower_id)
    @Query("SELECT COUNT(f) FROM Friendship f WHERE f.follower.id = :userId")
    long countFollowing(Long userId);
}