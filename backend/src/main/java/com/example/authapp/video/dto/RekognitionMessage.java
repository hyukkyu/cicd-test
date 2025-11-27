package com.example.authapp.video.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class RekognitionMessage {
    @JsonProperty("JobId")
    private String jobId;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("JobTag")
    private String jobTag;

    @JsonProperty("Video")
    private VideoInfo video;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoInfo {
        @JsonProperty("S3ObjectName")
        private String s3ObjectName;

        @JsonProperty("S3Bucket")
        private String s3Bucket;
    }
}
