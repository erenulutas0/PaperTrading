package com.finance.core.repository;

import com.finance.core.domain.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {
}

