package com.example.authapp.moderation.client;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.example.authapp.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

@Component
public class S3Uploader {

    private static final Logger log = LoggerFactory.getLogger(S3Uploader.class);

    private final AmazonS3 amazonS3;
    private final AppProperties appProperties;

    public S3Uploader(AmazonS3 amazonS3, AppProperties appProperties) {
        this.amazonS3 = amazonS3;
        this.appProperties = appProperties;
    }

    public UploadedObject upload(MultipartFile file, String directory) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어있습니다.");
        }
        String bucket = appProperties.getAws().getBucketName();
        String normalizedDir = directory == null ? "uploads" : directory.replaceAll("^/+", "").replaceAll("/+$", "");
        String sanitizedName = sanitize(file.getOriginalFilename());
        String key = String.format(Locale.ROOT, "%s/%s_%s", normalizedDir, UUID.randomUUID(), sanitizedName);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

        try (InputStream inputStream = file.getInputStream()) {
            amazonS3.putObject(bucket, key, inputStream, metadata);
        }

        String url = buildPublicUrl(bucket, key);
        log.info("Uploaded media file to S3 bucket={} key={} url={}", bucket, key, url);
        return new UploadedObject(bucket, key, url, file.getContentType(), file.getSize());
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "media";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String buildPublicUrl(String bucket, String key) {
        String cloudfrontDomain = appProperties.getAws().getCloudfrontDomain();
        if (cloudfrontDomain != null && !cloudfrontDomain.isBlank()) {
            String normalized = cloudfrontDomain.trim();
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "https://" + normalized;
            }
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized + "/" + key;
        }
        return amazonS3.getUrl(bucket, key).toString();
    }

    public record UploadedObject(
            String bucket,
            String key,
            String url,
            String contentType,
            long size
    ) {
        public boolean isImage() {
            return contentType != null && contentType.startsWith("image/");
        }

        public boolean isVideo() {
            return contentType != null && contentType.startsWith("video/");
        }
    }
}
