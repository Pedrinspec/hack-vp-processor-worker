package com.fiap.vp_processor_worker.domain.service.impl;

import com.fiap.vp_processor_worker.application.ports.output.MessageOutput;
import com.fiap.vp_processor_worker.application.ports.output.S3Output;
import com.fiap.vp_processor_worker.domain.exceptions.NoSuchVideoException;
import com.fiap.vp_processor_worker.domain.model.StatusUpdate;
import com.fiap.vp_processor_worker.domain.model.enums.StatusEnum;
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
    private final MessageOutput messageOutput;

    @Override
    public void execute(ProcessRequest processRequest) {
        if (!s3Output.exists(processRequest.getKey())) {
            log.info("Video Processor Service does not exist");

            messageOutput.sendStatusUpdate(StatusUpdate.builder()
                    .uploadId(processRequest.getUploadId())
                    .status(StatusEnum.PROCESSING_ERROR)
                    .build());

            throw new NoSuchVideoException(processRequest);
        }
        log.info("Video existe");

        messageOutput.sendStatusUpdate(StatusUpdate.builder()
                .uploadId(processRequest.getUploadId())
                .status(StatusEnum.PROCESSING)
                .build());

        s3Output.generateFramesAndZipToS3(processRequest.getUploadId(), processRequest.getKey());
        messageOutput.sendStatusUpdate(StatusUpdate.builder()
                .uploadId(processRequest.getUploadId())
                .status(StatusEnum.PROCESSED)
                .build());
        log.info("Video processado com sucesso");
    }
}
