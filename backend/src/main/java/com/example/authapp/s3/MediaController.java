package com.example.authapp.s3;

import com.example.authapp.s3.dto.DeleteMediaRequest;
import com.example.authapp.s3.dto.PresignedUploadRequest;
import com.example.authapp.s3.dto.PresignedUploadResponse;
import com.example.authapp.s3.dto.UploadMediaResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;

@RestController
@RequestMapping("/api/media")
@Validated
public class MediaController {

    private final S3Service s3Service;

    public MediaController(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/presigned")
    public ResponseEntity<PresignedUploadResponse> createPresignedUpload(@Valid @RequestBody PresignedUploadRequest request) {
        S3Service.PresignedUpload presigned = s3Service.generatePresignedUpload(request.directory(), request.contentType());
        return ResponseEntity.ok(PresignedUploadResponse.from(presigned));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<UploadMediaResponse> uploadMedia(@RequestParam("file") MultipartFile file,
                                                           @RequestParam(value = "directory", defaultValue = "uploads") String directory) throws IOException {
        S3Service.UploadResult result = s3Service.uploadWithMeta(file, directory);
        return ResponseEntity.ok(UploadMediaResponse.from(result));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/delete")
    public ResponseEntity<Void> deleteMedia(@Valid @RequestBody DeleteMediaRequest request) {
        s3Service.deleteObject(request.key());
        return ResponseEntity.noContent().build();
    }
}
