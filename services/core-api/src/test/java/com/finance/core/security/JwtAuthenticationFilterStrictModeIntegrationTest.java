package com.finance.core.security;

import com.finance.core.domain.AppUser;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.auth.jwt.allow-legacy-user-id-header=false")
@AutoConfigureMockMvc
class JwtAuthenticationFilterStrictModeIntegrationTest {

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
    void requestWithLegacyHeaderOnly_whenStrictModeEnabled_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/preferences")
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithBearer_whenStrictModeEnabled_shouldBeAllowed() throws Exception {
        AppUser user = userRepository.save(AppUser.builder()
                .username("strict-mode-user")
                .email("strict-mode-user@test.com")
                .password("plaintext")
                .build());
        String token = jwtTokenService.generateAccessToken(user.getId(), user.getUsername());

        mockMvc.perform(get("/api/v1/users/me/preferences")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
