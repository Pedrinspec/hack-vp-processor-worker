package com.fiap.vp_processor_worker.infrastructure.adapter.output;

import com.fiap.vp_processor_worker.application.ports.output.CacheOutput;
import com.fiap.vp_processor_worker.application.ports.output.MessageOutput;
import com.fiap.vp_processor_worker.application.ports.output.S3Output;
import com.fiap.vp_processor_worker.domain.model.ProcessingStatus;
import com.fiap.vp_processor_worker.domain.service.model.UploadError;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Log4j2
@Component
@RequiredArgsConstructor
public class S3OutputImpl implements S3Output {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final CacheOutput cacheOutput;
    private final MessageOutput messageOutput;
    @Value("${aws.s3.bucket.video}")
    private String videoBucket;
    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Override
    public boolean exists(String key) {
        try {
            log.info("Procurando por vídeo {}", key);
            s3Client.headObject(HeadObjectRequest.builder().bucket(videoBucket).key(key).build());
            log.info("Busca realizada");
            return true;
        }catch (S3Exception e){
            if (e.statusCode() == 404) {
                log.warn("Objeto não encontrado: {}", key);
                return false;
            }

            log.error("Erro ao consultar S3", e);
            throw e;
        }
    }

    @Override
    public void generateFramesAndZipToS3(UUID uploadId, String videoKey) {

        String zipKey = buildZipKey(videoKey);
        log.info("Starting frame generation for {}", videoKey);
        ProcessingStatus processingStatus = cacheOutput.get(uploadId);

        int startSecond = 0;
        if (processingStatus != null && "PROCESSING".equals(processingStatus.getStatus())) {

            startSecond = processingStatus.getLastSecondProcessed();

            log.info("Resuming from second {}", startSecond);

        } else {
            cacheOutput.save(
                    uploadId,
                    new ProcessingStatus("PROCESSING", 0, System.currentTimeMillis())
            );
        }


        CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(videoBucket)
                        .key(zipKey)
                        .contentType("application/zip")
                        .build()
        );

        String multipartUploadId = createResponse.uploadId();
        List<CompletedPart> completedParts = new ArrayList<>();

        try {

            String presignedUrl = getPresignedUrl(videoKey);

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-threads", "0",
                    "-ss", String.valueOf(startSecond),
                    "-skip_frame", "nokey",
                    "-i", presignedUrl,
                    "-vf", "fps=1",
                    "-vsync", "vfr",
                    "-q:v", "2",
                    "-f", "image2pipe",
                    "-vcodec", "mjpeg",
                    "pipe:1"
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            InputStream ffmpegOutput = new BufferedInputStream(process.getInputStream(), 8 * 1024 * 1024);

            PipedOutputStream zipOutPipe = new PipedOutputStream();
            PipedInputStream zipInputPipe = new PipedInputStream(zipOutPipe, 20 * 1024 * 1024);

            final int startSecondSnapshot = startSecond;

            Thread zipThread = new Thread(() -> {

                try (ZipOutputStream zipOut =
                             new ZipOutputStream(new BufferedOutputStream(zipOutPipe))) {

                    writeFramesToZip(
                            uploadId,
                            ffmpegOutput,
                            zipOut,
                            startSecondSnapshot
                    );

                } catch (Exception e) {

                    messageOutput.sendFailMessage(UploadError.builder()
                            .videoId(uploadId)
                            .reason("Unexpected error")
                            .details(e.getMessage())
                            .build());
                    throw new RuntimeException(e);

                }
            });

            zipThread.start();

            uploadMultipartStream(zipKey, multipartUploadId, zipInputPipe, completedParts);

            zipThread.join();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                messageOutput.sendFailMessage(UploadError.builder()
                        .videoId(uploadId)
                        .reason(String.valueOf(exitCode))
                        .details("FFmpeg failed with code " + exitCode)
                        .build());
                throw new RuntimeException("FFmpeg failed with code " + exitCode);
            }

            s3Client.completeMultipartUpload(
                    CompleteMultipartUploadRequest.builder()
                            .bucket(videoBucket)
                            .key(zipKey)
                            .uploadId(multipartUploadId)
                            .multipartUpload(CompletedMultipartUpload.builder()
                                    .parts(completedParts)
                                    .build())
                            .build()
            );


            cacheOutput.save(
                    uploadId,
                    new ProcessingStatus(
                            "COMPLETED",
                            startSecond,
                            System.currentTimeMillis()
                    )
            );

            log.info("Upload complete for {}", zipKey);

        } catch (Exception e) {

            log.error("Error processing video", e);

            s3Client.abortMultipartUpload(
                    AbortMultipartUploadRequest.builder()
                            .bucket(videoBucket)
                            .key(zipKey)
                            .uploadId(multipartUploadId)
                            .build()
            );

            cacheOutput.save(
                    uploadId,
                    new ProcessingStatus(
                            "FAILED",
                            startSecond,
                            System.currentTimeMillis()
                    )
            );

            throw new RuntimeException(e);
        }
    }

    private String getPresignedUrl(String videoKey) {
        PresignedGetObjectRequest presigned =
                s3Presigner.presignGetObject(builder -> builder
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(videoBucket)
                                .key(videoKey)
                                .build())
                        .signatureDuration(Duration.ofHours(6)));

        return presigned.url().toString();
    }

    private void uploadMultipartStream(
            String zipKey,
            String uploadId,
            InputStream input,
            List<CompletedPart> completedParts
    ) throws IOException {

        final int PART_SIZE = 8 * 1024 * 1024; // 8MB (seguro > 5MB)
        byte[] buffer = new byte[PART_SIZE];

        int partNumber = 1;
        int bytesRead;

        while ((bytesRead = readFully(input, buffer)) > 0) {

            UploadPartResponse uploadPartResponse = s3Client.uploadPart(
                    UploadPartRequest.builder()
                            .bucket(videoBucket)
                            .key(zipKey)
                            .uploadId(uploadId)
                            .partNumber(partNumber)
                            .contentLength((long) bytesRead)
                            .build(),
                    RequestBody.fromBytes(
                            bytesRead == PART_SIZE ? buffer : Arrays.copyOf(buffer, bytesRead)
                    )
            );

            completedParts.add(
                    CompletedPart.builder()
                            .partNumber(partNumber)
                            .eTag(uploadPartResponse.eTag())
                            .build()
            );

            partNumber++;
        }
    }

    private int readFully(InputStream input, byte[] buffer) throws IOException {

        int totalRead = 0;

        while (totalRead < buffer.length) {
            int read = input.read(buffer, totalRead, buffer.length - totalRead);
            if (read == -1) break;
            totalRead += read;
        }

        return totalRead;
    }

    private void writeFramesToZip(UUID uploadId,
                                  InputStream ffmpegOutput,
                                  ZipOutputStream zipOut,
                                  int startSecond) throws IOException {

        int prev = -1;
        int current;

        int frameIndex = 0;
        int secondProcessed = startSecond;

        ZipEntry currentEntry = null;

        while ((current = ffmpegOutput.read()) != -1) {

            if (prev == 0xFF && current == 0xD8) {

                currentEntry = new ZipEntry(
                        String.format("frames/frame_%05d.jpg", frameIndex++)
                );

                zipOut.putNextEntry(currentEntry);

                zipOut.write(prev);
                zipOut.write(current);

                prev = current;
                continue;
            }

            if (currentEntry != null) {

                zipOut.write(current);

                if (prev == 0xFF && current == 0xD9) {

                    zipOut.closeEntry();

                    secondProcessed++;

                    if (frameIndex % 10 == 0) {

                        cacheOutput.save(
                                uploadId,
                                new ProcessingStatus(
                                        "PROCESSING",
                                        secondProcessed,
                                        System.currentTimeMillis()
                                )
                        );

                        log.info("Second processed {}", secondProcessed);
                    }

                    currentEntry = null;
                }
            }

            prev = current;
        }
    }

    private String buildZipKey(String videoKey) {

        int dotIndex = videoKey.lastIndexOf('.');
        String base = dotIndex > 0 ? videoKey.substring(0, dotIndex) : videoKey;

        return base + "/frames.zip";
    }
}
