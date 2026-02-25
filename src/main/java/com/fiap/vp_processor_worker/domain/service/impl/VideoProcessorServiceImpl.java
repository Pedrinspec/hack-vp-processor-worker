package com.fiap.vp_processor_worker.domain.service.impl;

import com.fiap.vp_processor_worker.application.ports.output.S3Output;
import com.fiap.vp_processor_worker.domain.service.VideoProcessorService;
import com.fiap.vp_processor_worker.domain.service.model.ProcessRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class VideoProcessorServiceImpl implements VideoProcessorService {

    private final S3Output s3Output;

    @Override
    public void execute(ProcessRequest processRequest) {
        if (!s3Output.exists(processRequest.getKey())) {
            throw new RuntimeException();
        }
        log.info("Video existe");

        s3Output.generateFramesAndZipToS3(processRequest.getKey());
        log.info("Video processado com sucesso");

    }
}
