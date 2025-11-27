package com.example.authapp.report;

import com.example.authapp.user.User;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ReportType type;

    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    private User reporter;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private LocalDateTime createDate;

    @Convert(converter = ReportStatusConverter.class)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private ReportAction action;

    @Column(length = 500)
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_id")
    private User processedBy;

    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = ReportStatus.PENDING;
        }
        if (createDate == null) {
            createDate = LocalDateTime.now();
        }
    }
}
