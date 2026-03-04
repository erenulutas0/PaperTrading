package com.finance.core.security;

import com.finance.core.domain.AppUser;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void requestWithBearerOnly_shouldPopulateLegacyHeaderForExistingControllers() throws Exception {
        AppUser user = userRepository.save(AppUser.builder()
                .username("token-user")
                .email("token-user@test.com")
                .password("plaintext")
                .build());
        String token = jwtTokenService.generateAccessToken(user.getId(), user.getUsername());

        mockMvc.perform(get("/api/v1/users/me/preferences")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void requestWithBearerAndMismatchedLegacyHeader_shouldReturnUnauthorized() throws Exception {
        AppUser tokenUser = userRepository.save(AppUser.builder()
                .username("token-user")
                .email("token-user@test.com")
                .password("plaintext")
                .build());
        AppUser otherUser = userRepository.save(AppUser.builder()
                .username("other-user")
                .email("other-user@test.com")
                .password("plaintext")
                .build());
        String token = jwtTokenService.generateAccessToken(tokenUser.getId(), tokenUser.getUsername());

        mockMvc.perform(get("/api/v1/users/me/preferences")
                .header("Authorization", "Bearer " + token)
                .header("X-User-Id", otherUser.getId().toString()))
                .andExpect(status().isUnauthorized());
    }
}
