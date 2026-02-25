package com.fiap.vp_processor_worker.infrastructure.adapter.input;

import com.fiap.vp_processor_worker.application.usecase.VideoProcessorUseCase;
import com.fiap.vp_processor_worker.domain.service.model.ProcessRequest;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProcessorListener {

    private final VideoProcessorUseCase videoProcessorUseCase;
    private final Gson gson;

    @KafkaListener(groupId = "${kafka.consumer.group-id}", topics = {"${kafka.consumer.topic}"}, containerFactory = "customKafkaTemplate")
    public void processorListener(String processRequestJson) {
        ProcessRequest processRequest = gson.fromJson(processRequestJson, ProcessRequest.class);
        videoProcessorUseCase.execute(processRequest);
    }
}
