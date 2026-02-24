package com.fiap.vp_processor_worker.application.ports.output;

public interface S3Output {
    boolean exists(String key);

    void generateFramesAndZipToS3(String videoKey);
}
