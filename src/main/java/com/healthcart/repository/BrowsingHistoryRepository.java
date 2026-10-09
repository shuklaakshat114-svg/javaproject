package com.healthcart.repository;

import com.healthcart.domain.BrowsingHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrowsingHistoryRepository extends JpaRepository<BrowsingHistory, Long> {
    List<BrowsingHistory> findByUserIdOrderByViewedAtDesc(Long userId, Pageable pageable);
    Optional<BrowsingHistory> findByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserId(Long userId);
}
