package com.studyhub.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import org.springframework.security.authentication.ProviderManager;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.DispatcherType;

@Configuration
    public class SecurityConfig {
        @Bean
        public AuthenticationProvider authenticationProvider(
                CustomUserDetailsService userDetailsService,
                PasswordEncoder passwordEncoder
        ) {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
            provider.setPasswordEncoder(passwordEncoder);
            return provider;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                AuthenticationProvider authenticationProvider,
                JwtAuthenticationFilter jwtAuthenticationFilter
        ) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authenticationProvider(authenticationProvider)
                    .authorizeHttpRequests(authorize -> authorize
                            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            .requestMatchers(
                            "/api/auth/register",
                            "/api/auth/login",
                            "/actuator/health")
                            .permitAll()
                            .anyRequest()
                            .authenticated()
                    )
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(
                                    (request, response, authException) -> {
                                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                            })
                    )

                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }

        @Bean
        public ProviderManager authenticationManager(
                AuthenticationProvider authenticationProvider
        ) {
            return new ProviderManager(authenticationProvider);
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }