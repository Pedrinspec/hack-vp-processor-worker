package com.fiap.vp_processor_worker.application.ports.output;

import java.util.UUID;

public interface S3Output {
    boolean exists(String key);

    void generateFramesAndZipToS3(UUID uploadId, String videoKey);
}
