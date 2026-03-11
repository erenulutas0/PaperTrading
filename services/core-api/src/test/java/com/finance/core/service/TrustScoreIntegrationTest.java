package com.finance.core.service;

import com.finance.core.domain.AnalysisPost;
import com.finance.core.domain.AppUser;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional // Rolls back the database changes after each test automatically
class TrustScoreIntegrationTest {

    @Autowired
    private TrustScoreService trustScoreService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AnalysisPostRepository analysisPostRepository;

    private AppUser goodUser;
    private AppUser badUser;

    @BeforeEach
    void setUp() {
        goodUser = new AppUser();
        goodUser.setUsername("goodUserTest");
        goodUser.setPassword("password");
        goodUser.setEmail("good@test.com");
        goodUser.setTrustScore(50.0);
        goodUser = userRepository.save(goodUser);

        badUser = new AppUser();
        badUser.setUsername("badUserTest");
        badUser.setPassword("password");
        badUser.setEmail("bad@test.com");
        badUser.setTrustScore(50.0);
        badUser = userRepository.save(badUser);

        // Good User has 2 hits and 0 misses -> 100% win rate
        savePost(goodUser, AnalysisPost.Outcome.HIT);
        savePost(goodUser, AnalysisPost.Outcome.HIT);

        // Bad User has 1 hit and 3 misses -> 25% win rate
        savePost(badUser, AnalysisPost.Outcome.HIT);
        savePost(badUser, AnalysisPost.Outcome.MISSED);
        savePost(badUser, AnalysisPost.Outcome.MISSED);
        savePost(badUser, AnalysisPost.Outcome.MISSED);
    }

    @Test
    void testComputeTrustScores() {
        // Initially both are 50.0
        assertEquals(50.0, goodUser.getTrustScore(), 0.001);
        assertEquals(50.0, badUser.getTrustScore(), 0.001);

        // Run the trust score computation
        trustScoreService.computeTrustScores();

        // Refetch users explicitly if they are not in the same session,
        // though Transactional usually caches it or we can findById
        AppUser updatedGoodUser = userRepository.findById(goodUser.getId()).orElseThrow();
        AppUser updatedBadUser = userRepository.findById(badUser.getId()).orElseThrow();

        // Good user: 2/2 resolved predictions -> trust stays above baseline, but no
        // longer spikes unrealistically on tiny samples
        assertEquals(53.1, updatedGoodUser.getTrustScore(), 0.001);

        // Bad user: 1/4 resolved predictions -> score dips, but remains near neutral
        // until more evidence accumulates
        assertEquals(49.4, updatedBadUser.getTrustScore(), 0.001);
    }

    private void savePost(AppUser author, AnalysisPost.Outcome outcome) {
        AnalysisPost post = AnalysisPost.builder()
                .authorId(author.getId())
                .title("Test Post")
                .content("Test Content")
                .instrumentSymbol("BTCUSDT")
                .direction(AnalysisPost.Direction.BULLISH)
                .priceAtCreation(java.math.BigDecimal.valueOf(50000.0))
                .targetPrice(java.math.BigDecimal.valueOf(60000.0))
                .stopPrice(java.math.BigDecimal.valueOf(40000.0))
                .outcome(outcome)
                .createdAt(LocalDateTime.now())
                .deleted(false)
                .build();
        analysisPostRepository.save(post);
    }
}
