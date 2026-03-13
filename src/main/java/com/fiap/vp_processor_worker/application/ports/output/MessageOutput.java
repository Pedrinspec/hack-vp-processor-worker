package com.fiap.vp_processor_worker.application.ports.output;


import com.fiap.vp_processor_worker.domain.model.StatusUpdate;
import com.fiap.vp_processor_worker.domain.service.model.UploadError;

public interface MessageOutput {
        void sendFailMessage(UploadError uploadError);
        void sendStatusUpdate(StatusUpdate statusUpdate);
}
