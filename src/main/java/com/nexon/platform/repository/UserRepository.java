package com.nexon.platform.repository;

import com.nexon.platform.entity.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<PlatformUser, Long> {
    Optional<PlatformUser> findByNexonTag(String nexonTag);
}