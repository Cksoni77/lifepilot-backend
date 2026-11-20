package com.lifepilot.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifepilot.security.CurrentUser;
import com.lifepilot.security.JwtService;
import com.lifepilot.user.AppUser;
import com.lifepilot.user.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthServiceTest {
    @Mock private AppUserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private JwtService jwtService;

    private AutoCloseable mocks;
    private BCryptPasswordEncoder passwordEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AuthService(users, refreshTokens, passwordEncoder, jwtService,
                new CurrentUser(), 30);
        when(jwtService.issueAccessToken(any())).thenReturn("access-token");
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokens.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void registrationNormalizesEmailAndStoresOnlyPasswordHash() {
        when(users.existsByEmail("person@example.com")).thenReturn(false);
        String password = "strong-password-123";

        AuthDtos.AuthResponse response = service.register(
                new AuthDtos.RegisterRequest("  Person  ", " Person@Example.com ", password));

        assertEquals("access-token", response.accessToken());
        assertEquals("Person", response.user().name());
        assertEquals("person@example.com", response.user().email());
        assertTrue(passwordEncoder.matches(password, captureSavedUser().getPasswordHash()));
        verify(users).existsByEmail("person@example.com");
    }

    @Test
    void loginAuthenticatesAgainstStoredPasswordHash() {
        String password = "strong-password-123";
        AppUser user = new AppUser("person@example.com", "Person", passwordEncoder.encode(password));
        when(users.findByEmail("person@example.com")).thenReturn(Optional.of(user));

        AuthDtos.AuthResponse response = service.login(
                new AuthDtos.LoginRequest("Person@Example.com", password));

        assertEquals("access-token", response.accessToken());
        assertEquals("person@example.com", response.user().email());
        verify(users).findByEmail("person@example.com");
    }

    private AppUser captureSavedUser() {
        org.mockito.ArgumentCaptor<AppUser> captor =
                org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }
}
