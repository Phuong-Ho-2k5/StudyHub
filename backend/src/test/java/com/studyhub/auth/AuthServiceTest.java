package com.studyhub.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.studyhub.auth.dto.LoginRequest;
import com.studyhub.auth.dto.RegisterRequest;
import com.studyhub.auth.exception.DuplicateEmailException;
import com.studyhub.auth.exception.UserNotFoundException;
import com.studyhub.security.JwtService;
import com.studyhub.user.User;
import com.studyhub.user.UserRepository;
import com.studyhub.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwords;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwt;
    @InjectMocks private AuthService service;

    @Test
    void registrationPersistsNormalizedEmailEncodedPasswordAndUserRole() {
        when(passwords.encode("Secret123!")).thenReturn("encoded-password");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 42L);
            return user;
        });

        var response = service.register(new RegisterRequest("  Learner@Example.COM  ", "Secret123!", "Learner"));

        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("learner@example.com", saved.getValue().getEmail());
        assertEquals("encoded-password", saved.getValue().getPasswordHash());
        assertEquals(UserRole.USER, saved.getValue().getRole());
        assertEquals(42L, response.id());
        assertEquals("learner@example.com", response.email());
        assertEquals("Learner", response.name());
        verify(users).existsByEmail("learner@example.com");
    }

    @Test
    void duplicateNormalizedEmailDoesNotEncodeOrSave() {
        when(users.existsByEmail("learner@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> service.register(
                new RegisterRequest("  Learner@Example.COM  ", "Secret123!", "Learner")));

        verify(users, never()).save(any());
        verifyNoInteractions(passwords, authenticationManager, jwt);
    }

    @Test
    void loginAuthenticatesNormalizedEmailBeforeIssuingTokenForAuthenticatedPrincipal() {
        when(users.existsByEmail("learner@example.com")).thenReturn(true);
        var principal = org.springframework.security.core.userdetails.User
                .withUsername("learner@example.com").password("encoded-password").roles("USER").build();
        var authenticated = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
        when(jwt.generateToken(principal)).thenReturn("signed-access-token");

        var response = service.login(new LoginRequest("  Learner@Example.COM  ", "Secret123!"));

        assertEquals("signed-access-token", response.accessToken());
        var credentials = ArgumentCaptor.forClass(Authentication.class);
        var order = inOrder(authenticationManager, jwt);
        order.verify(authenticationManager).authenticate(credentials.capture());
        assertEquals("learner@example.com", credentials.getValue().getName());
        assertEquals("Secret123!", credentials.getValue().getCredentials());
        assertFalse(credentials.getValue().isAuthenticated());
        order.verify(jwt).generateToken(principal);
    }

    @Test
    void unknownEmailCannotAuthenticateOrReceiveToken() {
        assertThrows(UserNotFoundException.class,
                () -> service.login(new LoginRequest("missing@example.com", "Secret123!")));

        verifyNoInteractions(authenticationManager, jwt);
    }

    @Test
    void incorrectPasswordDoesNotIssueToken() {
        when(users.existsByEmail("learner@example.com")).thenReturn(true);
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class,
                () -> service.login(new LoginRequest("learner@example.com", "WrongPass123!")));

        verifyNoInteractions(jwt);
    }
}
