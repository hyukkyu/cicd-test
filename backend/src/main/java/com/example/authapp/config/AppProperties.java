package com.example.authapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Aws aws = new Aws();
    private final Monitoring monitoring = new Monitoring();
    private final Moderation moderation = new Moderation();

    public Aws getAws() {
        return aws;
    }

    public Monitoring getMonitoring() {
        return monitoring;
    }

    public Moderation getModeration() {
        return moderation;
    }

    public static class Aws {
        private String accessKey;
        private String secretKey;
        private String bucketName;
        private String region;
        private String comprehendRegion;
        private String translateRegion;
        private String rekognitionRegion;
        private String snsTopicArn;
        private String cloudfrontDomain;

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getComprehendRegion() {
            return comprehendRegion;
        }

        public void setComprehendRegion(String comprehendRegion) {
            this.comprehendRegion = comprehendRegion;
        }

        public String getTranslateRegion() {
            return translateRegion;
        }

        public void setTranslateRegion(String translateRegion) {
            this.translateRegion = translateRegion;
        }

        public String getRekognitionRegion() {
            return rekognitionRegion;
        }

        public void setRekognitionRegion(String rekognitionRegion) {
            this.rekognitionRegion = rekognitionRegion;
        }

        public String getSnsTopicArn() {
            return snsTopicArn;
        }

        public void setSnsTopicArn(String snsTopicArn) {
            this.snsTopicArn = snsTopicArn;
        }

        public String getCloudfrontDomain() {
            return cloudfrontDomain;
        }

        public void setCloudfrontDomain(String cloudfrontDomain) {
            this.cloudfrontDomain = cloudfrontDomain;
        }
    }

    public static class Monitoring {
        private String grafanaUrl;
        private boolean ec2HealthEnabled = false;
        private String ec2InstanceId;
        private String ec2InstanceTagKey;
        private String ec2InstanceTagValue;

        public String getGrafanaUrl() {
            return grafanaUrl;
        }

        public void setGrafanaUrl(String grafanaUrl) {
            this.grafanaUrl = grafanaUrl;
        }

        public boolean isEc2HealthEnabled() {
            return ec2HealthEnabled;
        }

        public void setEc2HealthEnabled(boolean ec2HealthEnabled) {
            this.ec2HealthEnabled = ec2HealthEnabled;
        }

        public String getEc2InstanceId() {
            return ec2InstanceId;
        }

        public void setEc2InstanceId(String ec2InstanceId) {
            this.ec2InstanceId = ec2InstanceId;
        }

        public String getEc2InstanceTagKey() {
            return ec2InstanceTagKey;
        }

        public void setEc2InstanceTagKey(String ec2InstanceTagKey) {
            this.ec2InstanceTagKey = ec2InstanceTagKey;
        }

        public String getEc2InstanceTagValue() {
            return ec2InstanceTagValue;
        }

        public void setEc2InstanceTagValue(String ec2InstanceTagValue) {
            this.ec2InstanceTagValue = ec2InstanceTagValue;
        }
    }

    public static class Moderation {
        private double textBlockThreshold = 0.7;
        private double mediaBlockThreshold = 0.7;
        private double reviewThreshold = 0.5;
        private String pipelineTopicArn;
        private String pipelineQueueUrl;
        private String adminEmail;

        public double getTextBlockThreshold() {
            return textBlockThreshold;
        }

        public void setTextBlockThreshold(double textBlockThreshold) {
            this.textBlockThreshold = textBlockThreshold;
        }

        public double getMediaBlockThreshold() {
            return mediaBlockThreshold;
        }

        public void setMediaBlockThreshold(double mediaBlockThreshold) {
            this.mediaBlockThreshold = mediaBlockThreshold;
        }

        public double getReviewThreshold() {
            return reviewThreshold;
        }

        public void setReviewThreshold(double reviewThreshold) {
            this.reviewThreshold = reviewThreshold;
        }

        public String getPipelineTopicArn() {
            return pipelineTopicArn;
        }

        public void setPipelineTopicArn(String pipelineTopicArn) {
            this.pipelineTopicArn = pipelineTopicArn;
        }

        public String getPipelineQueueUrl() {
            return pipelineQueueUrl;
        }

        public void setPipelineQueueUrl(String pipelineQueueUrl) {
            this.pipelineQueueUrl = pipelineQueueUrl;
        }

        public String getAdminEmail() {
            return adminEmail;
        }

        public void setAdminEmail(String adminEmail) {
            this.adminEmail = adminEmail;
        }
    }
}
