package com.fiap.vp_processor_worker.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Value("${aws.region}")
    private String region;
    @Value("${aws.access-key}")
    private String accessKey;
    @Value("${aws.access-secret}")
    private String accessSecret;
    @Value("${aws.session-token:}")
    private String sessionToken;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(getCredentials())
                )
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(getCredentials()))
                .build();
    }

    private AwsCredentials getCredentials() {
        if (sessionToken != null && !sessionToken.isBlank()) {
            return AwsSessionCredentials.create(accessKey, accessSecret, sessionToken);
        }
        return software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, accessSecret);
    }
}
