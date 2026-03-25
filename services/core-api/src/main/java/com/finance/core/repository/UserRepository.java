package com.finance.core.repository;

import com.finance.core.domain.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<AppUser> findByIdIn(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :userId")
    Optional<AppUser> findByIdForUpdate(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AppUser u
            set u.followerCount = case
                when (u.followerCount + :delta) < 0 then 0
                else (u.followerCount + :delta)
            end
            where u.id = :userId
            """)
    int adjustFollowerCount(@Param("userId") UUID userId, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AppUser u
            set u.followingCount = case
                when (u.followingCount + :delta) < 0 then 0
                else (u.followingCount + :delta)
            end
            where u.id = :userId
            """)
    int adjustFollowingCount(@Param("userId") UUID userId, @Param("delta") int delta);

    @Query("""
            select u from AppUser u
            order by
                case when u.verified = true then 1 else 0 end desc,
                u.trustScore desc,
                u.followerCount desc,
                u.createdAt desc
            """)
    List<AppUser> findSuggestedAccounts(Pageable pageable);
}
