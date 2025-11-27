package com.example.authapp.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.comprehend.AmazonComprehend;
import com.amazonaws.services.comprehend.AmazonComprehendClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.translate.AmazonTranslate;
import com.amazonaws.services.translate.AmazonTranslateClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AwsS3Config {

    private final AppProperties appProperties;

    public AwsS3Config(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public AmazonS3 amazonS3Client() {
        return AmazonS3ClientBuilder
                .standard()
                .withCredentials(resolveCredentials())
                .withRegion(appProperties.getAws().getRegion())
                .build();
    }

    @Bean
    public AmazonRekognition amazonRekognitionClient() {
        String rekognitionRegion = appProperties.getAws().getRekognitionRegion();
        if (rekognitionRegion == null || rekognitionRegion.isBlank()) {
            rekognitionRegion = appProperties.getAws().getRegion();
        }
        return AmazonRekognitionClientBuilder
                .standard()
                .withCredentials(resolveCredentials())
                .withRegion(rekognitionRegion)
                .build();
    }

    @Bean
    public AmazonComprehend amazonComprehendClient() {
        String comprehendRegion = appProperties.getAws().getComprehendRegion();
        if (comprehendRegion == null || comprehendRegion.isBlank()) {
            comprehendRegion = appProperties.getAws().getRegion();
        }
        return AmazonComprehendClientBuilder
                .standard()
                .withCredentials(resolveCredentials())
                .withRegion(comprehendRegion)
                .build();
    }

    @Bean
    public AmazonTranslate amazonTranslateClient() {
        String translateRegion = appProperties.getAws().getTranslateRegion();
        if (translateRegion == null || translateRegion.isBlank()) {
            translateRegion = appProperties.getAws().getRegion();
        }
        return AmazonTranslateClientBuilder
                .standard()
                .withCredentials(resolveCredentials())
                .withRegion(translateRegion)
                .build();
    }

    @Bean
    @Primary
    public AmazonSNS amazonSNSClient() {
        return AmazonSNSClientBuilder
                .standard()
                .withCredentials(resolveCredentials())
                .withRegion(appProperties.getAws().getRegion())
                .build();
    }

    @Bean
    public AmazonSQS amazonSQSClient() {
        return AmazonSQSClientBuilder
                .standard()
                .withCredentials(resolveCredentials())
                .withRegion(appProperties.getAws().getRegion())
                .build();
    }

    @Bean
    public AmazonEC2 amazonEc2Client() {
        return AmazonEC2ClientBuilder
                .standard()
                .withCredentials(resolveCredentials())
                .withRegion(appProperties.getAws().getRegion())
                .build();
    }

    private AWSCredentialsProvider resolveCredentials() {
        String accessKey = appProperties.getAws().getAccessKey();
        String secretKey = appProperties.getAws().getSecretKey();

        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        }
        return DefaultAWSCredentialsProviderChain.getInstance();
    }
}
