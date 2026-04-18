package com.example.backend.repository;

import com.example.backend.entity.TweetRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TweetRatingRepository extends JpaRepository<TweetRating, Long> {

    Optional<TweetRating> findByUserIdAndTweetId(Long userId, Long tweetId);

    List<TweetRating> findByTweetId(Long tweetId);

    @Query("""
            SELECT
                r.tweet.id,
                COUNT(r),
                SUM((COALESCE(r.score1, 0) + COALESCE(r.score2, 0) + COALESCE(r.score3, 0) + COALESCE(r.score4, 0) + COALESCE(r.score5, 0))
                    * (CASE
                        WHEN COALESCE(u.reputation, 0) < 100 THEN 1.0
                        WHEN COALESCE(u.reputation, 0) < 500 THEN 2.0
                        ELSE 5.0
                    END)),
                SUM(5.0 * (CASE
                    WHEN COALESCE(u.reputation, 0) < 100 THEN 1.0
                    WHEN COALESCE(u.reputation, 0) < 500 THEN 2.0
                    ELSE 5.0
                END)),
                SUM(COALESCE(r.score1, 0) * (CASE
                    WHEN COALESCE(u.reputation, 0) < 100 THEN 1.0
                    WHEN COALESCE(u.reputation, 0) < 500 THEN 2.0
                    ELSE 5.0
                END)),
                SUM(COALESCE(r.score2, 0) * (CASE
                    WHEN COALESCE(u.reputation, 0) < 100 THEN 1.0
                    WHEN COALESCE(u.reputation, 0) < 500 THEN 2.0
                    ELSE 5.0
                END)),
                SUM(COALESCE(r.score3, 0) * (CASE
                    WHEN COALESCE(u.reputation, 0) < 100 THEN 1.0
                    WHEN COALESCE(u.reputation, 0) < 500 THEN 2.0
                    ELSE 5.0
                END)),
                SUM(COALESCE(r.score4, 0) * (CASE
                    WHEN COALESCE(u.reputation, 0) < 100 THEN 1.0
                    WHEN COALESCE(u.reputation, 0) < 500 THEN 2.0
                    ELSE 5.0
                END)),
                SUM(COALESCE(r.score5, 0) * (CASE
                    WHEN COALESCE(u.reputation, 0) < 100 THEN 1.0
                    WHEN COALESCE(u.reputation, 0) < 500 THEN 2.0
                    ELSE 5.0
                END)),
                SUM(CASE WHEN COALESCE(u.reputation, 0) >= 20 THEN 1 ELSE 0 END),
                AVG(CASE WHEN COALESCE(u.reputation, 0) >= 20
                    THEN (COALESCE(r.score1, 0) + COALESCE(r.score2, 0) + COALESCE(r.score3, 0) + COALESCE(r.score4, 0) + COALESCE(r.score5, 0)) / 5.0
                    ELSE NULL END)
            FROM TweetRating r
            JOIN r.user u
            WHERE r.tweet.id IN :tweetIds
            GROUP BY r.tweet.id
            """)
    List<Object[]> aggregateSignalsByTweetIds(@Param("tweetIds") List<Long> tweetIds);
}

