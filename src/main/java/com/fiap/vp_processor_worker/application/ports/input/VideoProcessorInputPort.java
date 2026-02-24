package com.fiap.vp_processor_worker.application.ports.input;

import com.fiap.vp_processor_worker.application.usecase.VideoProcessorUseCase;
import com.fiap.vp_processor_worker.domain.service.VideoProcessorService;
import com.fiap.vp_processor_worker.domain.service.model.ProcessRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
@RequiredArgsConstructor
public class VideoProcessorInputPort implements VideoProcessorUseCase {

    private final VideoProcessorService videoProcessorService;
    private final ExecutorService videoExecutor;

    @Override
    public void execute(ProcessRequest processRequest) {
        videoExecutor.submit(() -> videoProcessorService.execute(processRequest));
    }
}
