   package com.example.backend.repository;

import com.example.backend.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    List<UserSubscription> findByUserIdOrderByCreateTimeDesc(Long userId);

    List<UserSubscription> findBySubscriptionTypeAndTargetValue(String subscriptionType, String targetValue);

    List<UserSubscription> findBySubscriptionTypeOrderByCreateTimeDesc(String subscriptionType);

    Optional<UserSubscription> findByUserIdAndSubscriptionTypeAndTargetValue(Long userId, String subscriptionType, String targetValue);
}
