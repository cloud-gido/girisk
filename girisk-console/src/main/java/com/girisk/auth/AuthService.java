package com.girisk.auth;

import com.girisk.auth.dto.LoginRequest;
import com.girisk.auth.dto.LoginResponse;
import com.girisk.auth.dto.UserProfile;
import com.girisk.auth.model.SysUser;
import com.girisk.auth.repository.UserRepository;
import com.girisk.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        if (!user.enabled() || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        String token = jwtTokenProvider.createToken(user);
        return new LoginResponse(token, user.username(), user.displayName(), user.role());
    }

    public UserProfile profile(String username) {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return new UserProfile(user.username(), user.displayName(), user.role());
    }
}
