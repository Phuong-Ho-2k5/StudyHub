package com.studyhub.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyhub.user.User;
import com.studyhub.user.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional (readOnly = true)
    public UserDetails loadUserByUsername(String email){
        String emailLowerCase = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(emailLowerCase)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + emailLowerCase));
        return new CustomUserDetails(user);
    }
}
