package com.example.authapp.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterIdAndTargetIdAndType(Long reporterId, Long targetId, ReportType type);

    Page<Report> findByStatusOrderByCreateDateDesc(ReportStatus status, Pageable pageable);

    Page<Report> findAllByOrderByCreateDateDesc(Pageable pageable);

    long countByStatus(ReportStatus status);

    long countByReporterId(Long reporterId);
}
