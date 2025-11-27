package com.example.authapp.s3;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    private final AmazonS3 amazonS3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.cloudfront.domain}")
    private String cloudfrontDomain;

    @Value("${cloud.aws.region.static}")
    private String region;

    public S3Service(AmazonS3 amazonS3Client) {
        this.amazonS3Client = amazonS3Client;
    }

    public String upload(MultipartFile multipartFile, String dirName) throws IOException {
        return uploadWithMeta(multipartFile, dirName).publicUrl();
    }

    public UploadResult uploadWithMeta(MultipartFile multipartFile, String dirName) throws IOException {
        if (isVideo(multipartFile)) {
            return uploadVideo(multipartFile, dirName);
        }
        File uploadFile = convert(multipartFile)
                .orElseThrow(() -> new IllegalArgumentException("MultipartFile -> File로 전환이 실패했습니다."));

        return uploadInternal(uploadFile, multipartFile, dirName);
    }

    public void deleteObject(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("삭제할 객체 키가 비어 있습니다.");
        }
        amazonS3Client.deleteObject(bucket, key);
    }

    /**
     * Uploads a post attachment (image or video) to an appropriate prefix.
     * Video files are stored under {@code videos/}, other media under {@code posts/}.
     */
    public String uploadPostMedia(MultipartFile multipartFile) throws IOException {
        String dirName = determineDirectory(multipartFile);
        return upload(multipartFile, dirName);
    }

    private UploadResult uploadInternal(File uploadFile, MultipartFile originalFile, String dirName) {
        String fileName = dirName + "/" + UUID.randomUUID() + "_" + uploadFile.getName();
        log.info("Uploading file to S3 bucket='{}', key='{}', originalName='{}', size={} bytes, contentType={}",
                bucket, fileName, originalFile != null ? originalFile.getOriginalFilename() : "n/a",
                originalFile != null ? originalFile.getSize() : uploadFile.length(),
                originalFile != null ? originalFile.getContentType() : "unknown");
        String uploadImageUrl = putS3(uploadFile, fileName);
        log.info("Stored file in S3 key='{}', accessibleUrl='{}'", fileName, uploadImageUrl);
        removeNewFile(uploadFile);
        return new UploadResult(bucket, fileName, uploadImageUrl,
                originalFile != null ? originalFile.getContentType() : null,
                originalFile != null ? originalFile.getSize() : uploadFile.length());
    }

    private String putS3(File uploadFile, String fileName) {
        amazonS3Client.putObject(new PutObjectRequest(bucket, fileName, uploadFile));
        return buildPublicUrl(fileName);
    }

    /**
     * Builds candidate public URLs for an S3 object key that may have been returned to clients.
     * This mirrors the logic used when generating download URLs during upload.
     */
    public List<String> buildCandidateUrls(String objectKey) {
        List<String> candidates = new ArrayList<>();
        if (objectKey == null || objectKey.isBlank()) {
            return candidates;
        }

        if (cloudfrontDomain != null && !cloudfrontDomain.isBlank()) {
            candidates.add(normalizeDomain(cloudfrontDomain) + "/" + objectKey);
        }
        candidates.add(amazonS3Client.getUrl(bucket, objectKey).toString());

        String regionSegment = (region != null && !region.isBlank()) ? "." + region : "";
        candidates.add(String.format("https://%s.s3%s.amazonaws.com/%s", bucket, regionSegment, objectKey));
        if (region != null && !region.isBlank()) {
            candidates.add(String.format("https://s3.%s.amazonaws.com/%s/%s", region, bucket, objectKey));
        }
        return candidates;
    }

    public String buildPublicUrl(String key) {
        if (cloudfrontDomain != null && !cloudfrontDomain.isBlank()) {
            return normalizeDomain(cloudfrontDomain) + "/" + key;
        }
        return amazonS3Client.getUrl(bucket, key).toString();
    }

    private void removeNewFile(File targetFile) {
        if (targetFile.delete()) {
            // log.info("파일이 삭제되었습니다.");
        } else {
            // log.info("파일이 삭제되지 못했습니다.");
        }
    }

    private Optional<File> convert(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = originalFilename.substring(dotIndex);
            }
        }
        File convertFile = File.createTempFile("upload_", extension);
        try (FileOutputStream fos = new FileOutputStream(convertFile)) {
            fos.write(file.getBytes());
        }
        return Optional.of(convertFile);
    }

    private String determineDirectory(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".mkv")) {
                return "videos";
            }
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("video/")) {
            return "videos";
        }
        return "posts";
    }

    public PresignedUpload generatePresignedUpload(String directory, String contentType) {
        String sanitizedDirectory = sanitizeDirectory(directory);
        String fileName = sanitizedDirectory + "/" + UUID.randomUUID();
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, fileName)
                .withMethod(HttpMethod.PUT)
                .withExpiration(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        if (contentType != null && !contentType.isBlank()) {
            request.addRequestParameter("Content-Type", contentType);
        }
        URL url = amazonS3Client.generatePresignedUrl(request);
        return new PresignedUpload(
                url.toString(),
                buildPublicUrl(fileName),
                fileName,
                request.getExpiration().toInstant()
        );
    }

    private String sanitizeDirectory(String directory) {
        String normalized = (directory == null || directory.isBlank()) ? "posts" : directory.trim();
        Set<String> allowed = Set.of("posts", "profile-pictures", "gallery", "videos", "notices");
        if (!allowed.contains(normalized)) {
            return "posts";
        }
        return normalized;
    }

    private UploadResult uploadVideo(MultipartFile multipartFile, String dirName) throws IOException {
        String normalizedDir = (dirName == null || dirName.isBlank()) ? "posts" : dirName.trim();
        if (!normalizedDir.endsWith("/")) {
            normalizedDir += "/";
        }
        if (!normalizedDir.toLowerCase(java.util.Locale.ROOT).contains("videos/")) {
            normalizedDir += "videos/";
        }
        String key = normalizedDir + UUID.randomUUID() + "_" + multipartFile.getOriginalFilename();
        var metadata = new com.amazonaws.services.s3.model.ObjectMetadata();
        metadata.setContentLength(multipartFile.getSize());
        metadata.setContentType(multipartFile.getContentType());
        metadata.getUserMetadata().put("moderation-status", "pending");
        metadata.getUserMetadata().put("job-tag", key);
        amazonS3Client.putObject(new PutObjectRequest(bucket, key, multipartFile.getInputStream(), metadata));
        return new UploadResult(bucket, key, buildPublicUrl(key),
                multipartFile.getContentType(),
                multipartFile.getSize());
    }

    private boolean isVideo(MultipartFile multipartFile) {
        String filename = multipartFile.getOriginalFilename();
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".mkv");
        }
        String contentType = multipartFile.getContentType();
        return contentType != null && contentType.startsWith("video/");
    }

    private String normalizeDomain(String domain) {
        String normalized = domain.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public record PresignedUpload(
            String uploadUrl,
            String publicUrl,
            String key,
            Instant expiresAt
    ) {
    }

    public record UploadResult(
            String bucket,
            String key,
            String publicUrl,
            String contentType,
            long size
    ) {
    }
}
