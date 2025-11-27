package com.example.authapp.comment;

import com.example.authapp.comment.CommentStatus;
import com.example.authapp.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Transactional
    void deleteByAuthor(User author);

    List<Comment> findByPostIdOrderByCreateDateAsc(Long postId);

    long countByPostId(Long postId);

    Page<Comment> findByPostIdAndStatus(Long postId, CommentStatus status, Pageable pageable);

    Page<Comment> findByPostIdAndStatusIn(Long postId, Collection<CommentStatus> statuses, Pageable pageable);

    long countByPostIdAndStatus(Long postId, CommentStatus status);

    long countByPostIdAndStatusIn(Long postId, Collection<CommentStatus> statuses);

    long countByAuthorId(Long authorId);
}
