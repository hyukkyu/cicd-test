package com.example.authapp.video.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoModerationLambdaResult {

    private String jobId;
    private String status;
    private String jobTag;
    private String bucket;

    @JsonProperty("objectKey")
    private String objectKey;

    private List<Label> labels;

    private String moderationResultJson;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Label {
        @JsonProperty("Name")
        private String name;

        @JsonProperty("ParentName")
        private String parentName;

        @JsonProperty("Confidence")
        private Float confidence;
    }
}
