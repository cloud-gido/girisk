package com.girisk.config;

import com.girisk.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) return;
        String hash = passwordEncoder.encode("admin123");
        userRepository.insert("admin", hash, "系统管理员", "ADMIN");
        userRepository.insert("reviewer", passwordEncoder.encode("review123"), "审核员", "REVIEWER");
        userRepository.insert("viewer", passwordEncoder.encode("view123"), "观察员", "VIEWER");
    }
}
