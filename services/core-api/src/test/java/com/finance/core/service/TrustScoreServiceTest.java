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

        // userA: 80% Win Rate -> 50 + (80 - 50) * 0.8 = 50 + 24 = 74.0
        when(analyticsService.calculateWinRate(userA.getId())).thenReturn(80.0);

        // userB: 40% Win Rate -> 50 - (50 - 40) * 0.5 = 50 - 5 = 45.0
        when(analyticsService.calculateWinRate(userB.getId())).thenReturn(40.0);

        // userC: 0% Win Rate -> code logic: if (winRate > 0) is false, stays 50.0
        when(analyticsService.calculateWinRate(userC.getId())).thenReturn(0.0);

        // userD: 100% Win Rate -> 50 + (100 - 50) * 0.8 = 50 + 40 = 90.0
        when(analyticsService.calculateWinRate(userD.getId())).thenReturn(100.0);

        // Act
        trustScoreService.computeTrustScores();

        // Assert
        verify(userRepository, times(1)).saveAll(any(java.util.List.class));

        assertEquals(74.0, userA.getTrustScore(), 0.001);
        assertEquals(45.0, userB.getTrustScore(), 0.001);
        assertEquals(50.0, userC.getTrustScore(), 0.001);
        assertEquals(90.0, userD.getTrustScore(), 0.001);
    }

    @Test
    void computeTrustScores_capsLimits() {
        // Prepare a scenario where the formula goes beyond limits just in case
        // Although math limits it to 90 right now base on formula (50 + 50*0.8 = 90).
        // If we force an impossible win rate of 150%, it should cap at 100.0
        AppUser userExtremeUpper = new AppUser();
        userExtremeUpper.setId(UUID.randomUUID());

        AppUser userExtremeLower = new AppUser();
        userExtremeLower.setId(UUID.randomUUID());

        when(userRepository.findAll(PageRequest.of(0, 250))).thenReturn(
                new PageImpl<>(java.util.List.of(userExtremeUpper, userExtremeLower), PageRequest.of(0, 250), 2));

        // Extreme Upper: 150% Win Rate -> 50 + 100*0.8 = 130 -> Cap to 100
        when(analyticsService.calculateWinRate(userExtremeUpper.getId())).thenReturn(150.0);

        // Extreme Lower: -50% Win Rate -> Though theoretically impossible, let's say it
        // forces score below 0.
        // (Wait, winRate > 0 protects this. Let's make winRate = 1% which gives 50 -
        // 49*0.5 = 25.5.
        // We can't force < 0 with current formula logic easily (50 - 49.9*0.5 is
        // 25.05).
        // Let's at least test the 1% scenario)
        when(analyticsService.calculateWinRate(userExtremeLower.getId())).thenReturn(1.0);

        // Act
        trustScoreService.computeTrustScores();

        // Assert
        assertEquals(100.0, userExtremeUpper.getTrustScore(), 0.001);
        assertEquals(25.5, userExtremeLower.getTrustScore(), 0.001);
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

        trustScoreService.computeTrustScores();

        verify(userRepository, times(1)).findAll(PageRequest.of(0, 250));
        verify(userRepository, times(1)).findAll(PageRequest.of(1, 250));
        verify(userRepository, times(2)).saveAll(any(java.util.List.class));
    }
}
