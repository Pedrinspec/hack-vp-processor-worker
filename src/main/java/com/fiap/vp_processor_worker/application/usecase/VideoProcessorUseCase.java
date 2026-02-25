package com.fiap.vp_processor_worker.application.usecase;

import com.fiap.vp_processor_worker.domain.service.model.ProcessRequest;

public interface VideoProcessorUseCase {
    void execute(ProcessRequest processRequest);
}
