package com.healthcart.service;

import com.healthcart.domain.BrowsingHistory;
import com.healthcart.domain.Product;
import com.healthcart.domain.User;
import com.healthcart.repository.BrowsingHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BrowsingHistoryService {

    private final BrowsingHistoryRepository browsingHistoryRepository;

    public BrowsingHistoryService(BrowsingHistoryRepository browsingHistoryRepository) {
        this.browsingHistoryRepository = browsingHistoryRepository;
    }

    @Transactional
    public void recordView(User user, Product product) {
        if (user == null || product == null) {
            return;
        }

        Optional<BrowsingHistory> existingOpt = browsingHistoryRepository.findByUserIdAndProductId(user.getId(), product.getId());
        if (existingOpt.isPresent()) {
            BrowsingHistory history = existingOpt.get();
            history.setViewedAt(LocalDateTime.now());
            browsingHistoryRepository.save(history);
        } else {
            BrowsingHistory history = new BrowsingHistory(user, product);
            browsingHistoryRepository.save(history);
        }
    }

    @Transactional(readOnly = true)
    public List<BrowsingHistory> getRecentViews(Long userId, int limit) {
        return browsingHistoryRepository.findByUserIdOrderByViewedAtDesc(userId, PageRequest.of(0, limit));
    }

    @Transactional
    public void clearHistory(Long userId) {
        browsingHistoryRepository.deleteByUserId(userId);
    }
}
