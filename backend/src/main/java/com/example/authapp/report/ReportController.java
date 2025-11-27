package com.example.authapp.report;

import com.example.authapp.user.User;
import com.example.authapp.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/reports")
@Validated
public class ReportController {

    private final ReportService reportService;
    private final UserService userService;

    public ReportController(ReportService reportService, UserService userService) {
        this.reportService = reportService;
        this.userService = userService;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ReportResponse> createReport(@AuthenticationPrincipal UserDetails userDetails,
                                                       @Valid @RequestBody ReportRequest request) {
        User reporter = userService.findByUsername(userDetails.getUsername());
        boolean success = reportService.create(request.targetId(), request.type(), request.reason(), reporter);
        if (!success) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ReportResponse.duplicate());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportResponse.success());
    }
}
