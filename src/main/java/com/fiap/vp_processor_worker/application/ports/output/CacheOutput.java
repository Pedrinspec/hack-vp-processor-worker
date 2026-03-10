package com.fiap.vp_processor_worker.application.ports.output;

import com.fiap.vp_processor_worker.domain.model.ProcessingStatus;

import java.util.UUID;

public interface CacheOutput {
    void save(UUID uploadId, ProcessingStatus status);
    ProcessingStatus get(UUID uploadId);
}
