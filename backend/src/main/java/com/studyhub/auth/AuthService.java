package com.studyhub.auth;

import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.studyhub.auth.dto.RegisterRequest;
import com.studyhub.auth.dto.RegisterResponse;
import com.studyhub.user.User;
import com.studyhub.user.UserRole;
import com.studyhub.user.UserRepository;
import com.studyhub.auth.dto.LoginResponse;
import com.studyhub.auth.dto.LoginRequest;

import com.studyhub.auth.exception.DuplicateEmailException;
import com.studyhub.auth.exception.UserNotFoundException;

import com.studyhub.security.JwtService;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if(userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email already exists");
        }
        String encodedPassword = passwordEncoder.encode(request.password());
        User user = new User(email, encodedPassword, request.name(), UserRole.USER);
        userRepository.save(user);
        return new RegisterResponse(user.getId(), user.getEmail(), user.getName());
    }

    @Transactional 
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if(!userRepository.existsByEmail(email)) {
            throw new UserNotFoundException("User not found");
        }
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtService.generateToken(principal);
        return new LoginResponse(accessToken);
    }
}