package com.example.authapp.moderation.client;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.DetectModerationLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectModerationLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.ModerationLabel;
import com.amazonaws.services.rekognition.model.S3Object;
import com.example.authapp.config.AppProperties;
import com.example.authapp.moderation.model.MediaModerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RekognitionClient {

    private static final Logger log = LoggerFactory.getLogger(RekognitionClient.class);

    private final AmazonRekognition amazonRekognition;
    private final AppProperties appProperties;

    public RekognitionClient(AmazonRekognition amazonRekognition, AppProperties appProperties) {
        this.amazonRekognition = amazonRekognition;
        this.appProperties = appProperties;
    }

    public MediaModerationResult analyzeImage(String bucket, String key) {
        if (bucket == null || key == null) {
            return MediaModerationResult.empty();
        }
        DetectModerationLabelsRequest request = new DetectModerationLabelsRequest()
                .withMinConfidence((float) (appProperties.getModeration().getMediaBlockThreshold() * 100))
                .withImage(new Image().withS3Object(new S3Object().withBucket(bucket).withName(key)));
        DetectModerationLabelsResult result = amazonRekognition.detectModerationLabels(request);
        List<ModerationLabel> labels = result.getModerationLabels();
        double maxConfidence = labels.stream()
                .map(ModerationLabel::getConfidence)
                .max(Float::compareTo)
                .map(Float::doubleValue)
                .orElse(0.0);

        boolean blocked = maxConfidence >= appProperties.getModeration().getMediaBlockThreshold() * 100;
        List<String> names = labels.stream()
                .map(label -> label.getName() + "(" + label.getConfidence() + ")")
                .collect(Collectors.toList());
        log.debug("Rekognition scanned media key={} labels={}", key, names);
        return new MediaModerationResult(maxConfidence, blocked, names, blocked ? "MEDIA_FLAGGED" : "CLEAN");
    }
}
