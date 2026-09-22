package com.studyhub.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTests {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsUserByEmail() {
        User saved = userRepository.saveAndFlush(
                new User("student@example.com", "hashed-password", "Student", UserRole.USER));

        assertThat(userRepository.existsByEmail("student@example.com")).isTrue();
        assertThat(userRepository.findByEmail("student@example.com"))
                .isPresent()
                .get()
                .extracting(User::getId)
                .isEqualTo(saved.getId());
    }

    @Test
    void updatesTimestampWhenUserChanges() throws InterruptedException {
        User saved = userRepository.saveAndFlush(
                new User("updated@example.com", "hashed-password", "Original", UserRole.USER));
        var originalUpdatedAt = saved.getUpdatedAt();

        Thread.sleep(100);
        saved.rename("Updated");
        userRepository.saveAndFlush(saved);

        assertThat(saved.getUpdatedAt()).isAfter(originalUpdatedAt);
    }
}
