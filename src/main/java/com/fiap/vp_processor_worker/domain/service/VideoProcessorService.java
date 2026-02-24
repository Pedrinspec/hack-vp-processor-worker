package com.fiap.vp_processor_worker.domain.service;

import com.fiap.vp_processor_worker.domain.service.model.ProcessRequest;

public interface VideoProcessorService {
    void execute(ProcessRequest processRequest);
}
