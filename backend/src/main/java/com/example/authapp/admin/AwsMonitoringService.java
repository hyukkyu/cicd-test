package com.example.authapp.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;

import java.util.HashMap;
import java.util.Map;

@Service
public class AwsMonitoringService {

    private final S3Client s3Client;
    private final LambdaClient lambdaClient;
    private final Ec2Client ec2Client;

    public AwsMonitoringService(@Value("${cloud.aws.region.static}") String awsRegion) {
        Region region = Region.of(awsRegion);
        this.s3Client = S3Client.builder().region(region).build();
        this.lambdaClient = LambdaClient.builder().region(region).build();
        this.ec2Client = Ec2Client.builder().region(region).build();
    }

    public Map<String, String> checkS3Status() {
        Map<String, String> status = new HashMap<>();
        try {
            // 간단한 API 호출로 서비스 가용성 확인
            s3Client.listBuckets(ListBucketsRequest.builder().build());
            status.put("status", "UP");
            status.put("message", "S3 is reachable.");
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("message", "S3 check failed: " + e.getMessage());
        }
        return status;
    }

    public Map<String, String> checkLambdaStatus() {
        Map<String, String> status = new HashMap<>();
        try {
            // 간단한 API 호출로 서비스 가용성 확인
            lambdaClient.listFunctions(ListFunctionsRequest.builder().maxItems(1).build());
            status.put("status", "UP");
            status.put("message", "Lambda is reachable.");
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("message", "Lambda check failed: " + e.getMessage());
        }
        return status;
    }

    public Map<String, String> checkEc2Status() {
        Map<String, String> status = new HashMap<>();
        try {
            // 간단한 API 호출로 서비스 가용성 확인
            ec2Client.describeInstances(DescribeInstancesRequest.builder().maxResults(1).build());
            status.put("status", "UP");
            status.put("message", "EC2 is reachable.");
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("message", "EC2 check failed: " + e.getMessage());
        }
        return status;
    }

    public Map<String, Map<String, String>> getAllAwsServiceStatus() {
        Map<String, Map<String, String>> allStatus = new HashMap<>();
        allStatus.put("S3", checkS3Status());
        allStatus.put("Lambda", checkLambdaStatus());
        allStatus.put("EC2", checkEc2Status());
        return allStatus;
    }
}