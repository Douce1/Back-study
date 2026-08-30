package com.nexon.platform.service;

import com.nexon.platform.dto.UserCreateRequest;
import com.nexon.platform.dto.UserResponse;
import com.nexon.platform.entity.PlatformUser;
import com.nexon.platform.entity.UserRole;
import com.nexon.platform.repository.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class UserService {

    private static final String USER_CACHE_PREFIX = "user:cache:";

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserService(UserRepository userRepository, RedisTemplate<String, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        userRepository.findByNexonTag(request.getNexonTag())
                .ifPresent(u -> {
                    throw new IllegalArgumentException("이미 존재하는 NexonTag 입니다: " + request.getNexonTag());
                });

        PlatformUser newUser = new PlatformUser(request.getNexonTag(), "TEMP_PASSWORD", UserRole.ROLE_USER);
        PlatformUser savedUser = userRepository.save(newUser);

        return new UserResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        String cacheKey = USER_CACHE_PREFIX + userId;

        Object cachedObject = redisTemplate.opsForValue().get(cacheKey);
        if (cachedObject instanceof UserResponse userResponse) {
            return userResponse;
        }

        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));

        UserResponse response = new UserResponse(user);
        redisTemplate.opsForValue().set(cacheKey, response, Duration.ofMinutes(10));

        return response;
    }
}