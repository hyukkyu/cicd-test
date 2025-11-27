package com.example.authapp.post;

import com.example.authapp.service.dto.CategoryDistribution;
import com.example.authapp.service.dto.DetectionCount;
import com.example.authapp.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    Page<Post> findAll(Pageable pageable);

    List<Post> findTop10ByOrderByCreateDateDesc();

    Page<Post> findBySubBoardName(String subBoardName, Pageable pageable);

    Page<Post> findBySubBoardNameAndTabItem(String subBoardName, String tabItem, Pageable pageable);

    @Query("""
            SELECT p
            FROM Post p
            WHERE p.mainBoardName IN :mainBoardNames
              AND size(p.voter) >= :minVotes
            ORDER BY size(p.voter) DESC, p.createDate DESC
            """)
    Page<Post> findPopularPostsInMainBoards(@Param("mainBoardNames") List<String> mainBoardNames,
                                            @Param("minVotes") int minVotes,
                                            Pageable pageable);

    @Transactional
    void deleteByAuthor(User author);

    @Query("SELECT DISTINCT p.subBoardName FROM Post p WHERE p.mainBoardName = :mainBoardName")
    List<String> findDistinctSubBoardNameByMainBoardName(@Param("mainBoardName") String mainBoardName);

    List<Post> findByVoterContains(User user);

    List<Post> findByAuthor(User author);

    List<Post> findByStatus(PostStatus status);

    List<Post> findByAuthorId(Long authorId);

    List<Post> findByAuthorIdAndStatus(Long authorId, PostStatus status);

    long countByAuthorId(Long authorId);

    List<Post> findByModerationStatus(PostModerationStatus moderationStatus);

    long countByStatus(PostStatus status);

    long countByHarmfulTrue();

    @Query("""
            SELECT p
            FROM Post p
            JOIN p.fileUrls url
            WHERE url = :fileUrl
            """)
    List<Post> findByFileUrl(@Param("fileUrl") String fileUrl);

    long countByBlocked(boolean blocked);

    @Query("""
            SELECT COUNT(p)
            FROM Post p
            WHERE p.harmful = true
              AND p.reportedAt >= :since
            """)
    long countHarmfulSince(@Param("since") LocalDateTime since);

    @Query("""
            SELECT new com.example.authapp.service.dto.CategoryDistribution(
                COALESCE(p.mainBoardName, '기타'),
                COUNT(p)
            )
            FROM Post p
            GROUP BY p.mainBoardName
            ORDER BY COUNT(p) DESC
            """)
    List<CategoryDistribution> countPostsByMainBoard();

    @Query("""
            SELECT new com.example.authapp.service.dto.DetectionCount(
                CASE WHEN p.harmful = true THEN 'Harmful Detected' ELSE 'Clean' END,
                COUNT(p)
            )
            FROM Post p
            GROUP BY p.harmful
            """)
    List<DetectionCount> countHarmfulBreakdown();
}
