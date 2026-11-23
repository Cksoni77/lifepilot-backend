package com.lifepilot.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifepilot.security.CurrentUser;
import com.lifepilot.security.JwtService;
import com.lifepilot.TestAppProperties;
import com.lifepilot.user.AppUser;
import com.lifepilot.user.AppUserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

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
                new CurrentUser(), TestAppProperties.defaults());
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
        AppUser savedUser = captureSavedUser();
        assertTrue(passwordEncoder.matches(password, savedUser.getPasswordHash()));
        assertNotEquals(password, savedUser.getPasswordHash());
        verify(users).existsByEmail("person@example.com");
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(tokenCaptor.capture());
        assertTrue(tokenCaptor.getValue().isUsableAt(
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(29)));
        assertFalse(tokenCaptor.getValue().isUsableAt(
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(31)));
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

    @Test
    void refreshRevokesOldTokenAndCreatesANewToken() {
        String oldRawToken = "old-raw-refresh-token";
        AppUser user = new AppUser("person@example.com", "Person", "password-hash");
        RefreshToken oldToken = new RefreshToken(
                user, "stored-hash", OffsetDateTime.now(ZoneOffset.UTC).plusDays(2));
        when(refreshTokens.findByTokenHashForUpdate(any())).thenReturn(Optional.of(oldToken));

        AuthDtos.AuthResponse response = service.refresh(oldRawToken);

        assertNotEquals(oldRawToken, response.refreshToken());
        assertFalse(oldToken.isUsableAt(OffsetDateTime.now(ZoneOffset.UTC)));
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(refreshTokens).findByTokenHashForUpdate(hashCaptor.capture());
        assertNotEquals(oldRawToken, hashCaptor.getValue());
        assertEquals(64, hashCaptor.getValue().length());
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void invalidRefreshTokenIsRejected() {
        when(refreshTokens.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());

        Assertions.assertThrows(
                ResponseStatusException.class, () -> service.refresh("invalid-refresh-token"));
    }

    @Test
    void logoutRevokesAnExistingRefreshToken() {
        AppUser user = new AppUser("person@example.com", "Person", "password-hash");
        RefreshToken token = new RefreshToken(
                user, "stored-hash", OffsetDateTime.now(ZoneOffset.UTC).plusDays(2));
        when(refreshTokens.findByTokenHashForUpdate(any())).thenReturn(Optional.of(token));

        service.logout("raw-refresh-token");

        assertFalse(token.isUsableAt(OffsetDateTime.now(ZoneOffset.UTC)));
        verify(refreshTokens).findByTokenHashForUpdate(any());
    }

    private AppUser captureSavedUser() {
        org.mockito.ArgumentCaptor<AppUser> captor =
                org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }
}
