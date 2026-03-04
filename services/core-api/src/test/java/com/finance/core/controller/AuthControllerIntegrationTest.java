package com.finance.core.controller;

import com.finance.core.domain.AppUser;
import com.finance.core.repository.RefreshTokenRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.service.BinanceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BinanceService binanceService;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void register_shouldHashPasswordAndReturnJwtPayload() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "alice",
                          "email": "alice@test.com",
                          "password": "secret123"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        AppUser saved = userRepository.findByEmail("alice@test.com").orElseThrow();
        assertNotNull(saved.getPassword());
        assertTrue(saved.getPassword().startsWith("$2"));
        assertTrue(passwordEncoder.matches("secret123", saved.getPassword()));
    }

    @Test
    void login_shouldSupportLegacyPlaintextPasswordAndUpgradeHash() throws Exception {
        AppUser legacy = userRepository.save(AppUser.builder()
                .username("legacy")
                .email("legacy@test.com")
                .password("legacy-pass")
                .build());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "legacy@test.com",
                          "password": "legacy-pass"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(legacy.getId().toString()))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        AppUser upgraded = userRepository.findById(legacy.getId()).orElseThrow();
        assertTrue(upgraded.getPassword().startsWith("$2"));
        assertTrue(passwordEncoder.matches("legacy-pass", upgraded.getPassword()));
    }

    @Test
    void refresh_shouldRotateRefreshToken_andRejectOldToken() throws Exception {
        userRepository.save(AppUser.builder()
                .username("rotator")
                .email("rotator@test.com")
                .password(passwordEncoder.encode("secret123"))
                .build());

        String loginPayload = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "rotator@test.com",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginPayload);
        String firstRefreshToken = loginJson.path("refreshToken").asText();
        assertTrue(firstRefreshToken != null && !firstRefreshToken.isBlank());

        String refreshPayload = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(firstRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode refreshJson = objectMapper.readTree(refreshPayload);
        String rotatedRefreshToken = refreshJson.path("refreshToken").asText();
        assertTrue(rotatedRefreshToken != null && !rotatedRefreshToken.isBlank());
        assertTrue(!rotatedRefreshToken.equals(firstRefreshToken));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(firstRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withRefreshToken_shouldRevokeSession() throws Exception {
        userRepository.save(AppUser.builder()
                .username("logout-user")
                .email("logout-user@test.com")
                .password(passwordEncoder.encode("secret123"))
                .build());

        String loginPayload = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "logout-user@test.com",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginPayload);
        String refreshToken = loginJson.path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }
}
