package com.nexon.platform.service;

import com.nexon.platform.dto.AuthDto;
import com.nexon.platform.entity.PlatformUser;
import com.nexon.platform.jwt.JwtTokenProvider;
import com.nexon.platform.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public AuthDto.TokenResponse signup(AuthDto.LoginRequest request) {
        if (userRepository.findByNexonTag(request.getNexonTag()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 넥슨 계정 태그입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        PlatformUser newUser = new PlatformUser(request.getNexonTag(), encodedPassword, request.getRole());
        PlatformUser savedUser = userRepository.save(newUser);

        String accessToken = jwtTokenProvider.createAccessToken(
                savedUser.getUserId(),
                savedUser.getNexonTag(),
                savedUser.getRole().name()
        );

        return new AuthDto.TokenResponse(accessToken, savedUser.getUserId(), savedUser.getNexonTag());
    }

    @Transactional(readOnly = true)
    public AuthDto.TokenResponse login(AuthDto.LoginRequest request) {
        PlatformUser user = userRepository.findByNexonTag(request.getNexonTag())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 넥슨 계정입니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getUserId(),
                user.getNexonTag(),
                user.getRole().name()
        );

        return new AuthDto.TokenResponse(accessToken, user.getUserId(), user.getNexonTag());
    }
}