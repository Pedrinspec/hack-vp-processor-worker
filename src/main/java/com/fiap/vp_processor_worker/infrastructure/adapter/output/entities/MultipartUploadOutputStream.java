package com.fiap.vp_processor_worker.infrastructure.adapter.output.entities;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MultipartUploadOutputStream extends OutputStream {
    private final S3Client s3Client;
    private final String bucket;
    private final String key;
    private final String uploadId;

    private final int partSize;
    private final List<CompletedPart> completedParts = new ArrayList<>();

    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private int partNumber = 1;

    public MultipartUploadOutputStream(S3Client s3Client,
                                       String bucket,
                                       String key,
                                       String uploadId,
                                       int partSize) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.key = key;
        this.uploadId = uploadId;
        this.partSize = partSize;
    }

    @Override
    public void write(int b) throws IOException {
        buffer.write(b);
        flushIfNeeded();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        buffer.write(b, off, len);
        flushIfNeeded();
    }

    private void flushIfNeeded() {
        if (buffer.size() >= partSize) {
            uploadPart();
        }
    }

    private void uploadPart() {

        byte[] bytes = buffer.toByteArray();

        UploadPartResponse response = s3Client.uploadPart(
                UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) bytes.length)
                        .build(),
                RequestBody.fromBytes(bytes)
        );

        completedParts.add(
                CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(response.eTag())
                        .build()
        );

        partNumber++;
        buffer.reset();
    }

    public void complete() {

        if (buffer.size() > 0) {
            uploadPart();
        }

        s3Client.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .multipartUpload(
                                CompletedMultipartUpload.builder()
                                        .parts(completedParts)
                                        .build()
                        )
                        .build()
        );
    }

    public void abort() {
        s3Client.abortMultipartUpload(
                AbortMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .build()
        );
    }
}
