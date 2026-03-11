package com.finance.core.service;

import com.finance.core.domain.AppUser;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrustScoreServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PerformanceAnalyticsService analyticsService;

    @InjectMocks
    private TrustScoreService trustScoreService;

    private AppUser userA;
    private AppUser userB;
    private AppUser userC;
    private AppUser userD;

    @BeforeEach
    void setUp() {
        userA = new AppUser();
        userA.setId(UUID.randomUUID());
        userA.setTrustScore(50.0);

        userB = new AppUser();
        userB.setId(UUID.randomUUID());
        userB.setTrustScore(50.0);

        userC = new AppUser();
        userC.setId(UUID.randomUUID());
        userC.setTrustScore(50.0);

        userD = new AppUser();
        userD.setId(UUID.randomUUID());
        // Setup initial trust score differently to verify it gets overwritten
        userD.setTrustScore(20.0);
    }

    @Test
    void computeTrustScores_withVariousWinRates() {
        // Arrange
        when(userRepository.findAll(PageRequest.of(0, 250)))
                .thenReturn(new PageImpl<>(java.util.List.of(userA, userB, userC, userD), PageRequest.of(0, 250), 4));

        when(analyticsService.calculateWinRate(userA.getId())).thenReturn(80.0);
        when(analyticsService.countResolvedPredictions(userA.getId())).thenReturn(20L);
        when(analyticsService.calculateWinRate(userB.getId())).thenReturn(40.0);
        when(analyticsService.countResolvedPredictions(userB.getId())).thenReturn(20L);
        when(analyticsService.calculateWinRate(userC.getId())).thenReturn(0.0);
        when(analyticsService.countResolvedPredictions(userC.getId())).thenReturn(0L);
        when(analyticsService.calculateWinRate(userD.getId())).thenReturn(100.0);
        when(analyticsService.countResolvedPredictions(userD.getId())).thenReturn(2L);

        trustScoreService.computeTrustScores();

        verify(userRepository, times(1)).saveAll(any(java.util.List.class));

        assertEquals(76.71, userA.getTrustScore(), 0.001);
        assertEquals(48.14, userB.getTrustScore(), 0.001);
        assertEquals(50.0, userC.getTrustScore(), 0.001);
        assertEquals(63.5, userD.getTrustScore(), 0.001);
    }

    @Test
    void computeTrustScores_capsLimits() {
        AppUser userExtremeUpper = new AppUser();
        userExtremeUpper.setId(UUID.randomUUID());

        AppUser userExtremeLower = new AppUser();
        userExtremeLower.setId(UUID.randomUUID());

        when(userRepository.findAll(PageRequest.of(0, 250))).thenReturn(
                new PageImpl<>(java.util.List.of(userExtremeUpper, userExtremeLower), PageRequest.of(0, 250), 2));

        when(analyticsService.calculateWinRate(userExtremeUpper.getId())).thenReturn(150.0);
        when(analyticsService.countResolvedPredictions(userExtremeUpper.getId())).thenReturn(200L);
        when(analyticsService.calculateWinRate(userExtremeLower.getId())).thenReturn(1.0);
        when(analyticsService.countResolvedPredictions(userExtremeLower.getId())).thenReturn(200L);

        trustScoreService.computeTrustScores();

        assertEquals(100.0, userExtremeUpper.getTrustScore(), 0.001);
        assertEquals(5.9, userExtremeLower.getTrustScore(), 0.001);
    }

    @Test
    void computeTrustScores_processesAllPages() {
        List<AppUser> firstPageUsers = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            AppUser user = new AppUser();
            user.setId(UUID.randomUUID());
            firstPageUsers.add(user);
        }

        AppUser secondPageUser = new AppUser();
        secondPageUser.setId(UUID.randomUUID());

        when(userRepository.findAll(PageRequest.of(0, 250)))
                .thenReturn(new PageImpl<>(firstPageUsers, PageRequest.of(0, 250), 251));
        when(userRepository.findAll(PageRequest.of(1, 250)))
                .thenReturn(new PageImpl<>(java.util.List.of(secondPageUser), PageRequest.of(1, 250), 251));

        when(analyticsService.calculateWinRate(any(UUID.class))).thenReturn(50.0);
        when(analyticsService.countResolvedPredictions(any(UUID.class))).thenReturn(10L);

        trustScoreService.computeTrustScores();

        verify(userRepository, times(1)).findAll(PageRequest.of(0, 250));
        verify(userRepository, times(1)).findAll(PageRequest.of(1, 250));
        verify(userRepository, times(2)).saveAll(any(java.util.List.class));
    }
}
