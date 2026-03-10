package com.fiap.vp_processor_worker.domain.exceptions;

import com.fiap.vp_processor_worker.domain.service.model.ProcessRequest;

public class NoSuchVideoException extends RuntimeException {
    public NoSuchVideoException(ProcessRequest processRequest) {
        super(String.format("Vídeo with id %s doesn`t exist in path %s", processRequest.getUploadId(), processRequest.getKey()));
    }
}
