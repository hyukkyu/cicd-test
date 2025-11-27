package com.example.authapp.user;

import com.example.authapp.service.dto.DailySignupStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    long countByStatus(UserStatus status);

    @Query("""
            SELECT new com.example.authapp.service.dto.DailySignupStats(
                FUNCTION('date', u.createdAt),
                COUNT(u)
            )
            FROM User u
            WHERE u.createdAt BETWEEN :start AND :end
            GROUP BY FUNCTION('date', u.createdAt)
            ORDER BY FUNCTION('date', u.createdAt)
            """)
    List<DailySignupStats> countDailySignups(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);
}
