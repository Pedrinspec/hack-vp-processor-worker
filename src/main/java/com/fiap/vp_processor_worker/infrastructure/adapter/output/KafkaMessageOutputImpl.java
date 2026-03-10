package com.fiap.vp_processor_worker.infrastructure.adapter.output;

import com.fiap.vp_processor_worker.application.ports.output.MessageOutput;
import com.fiap.vp_processor_worker.domain.service.model.UploadError;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaMessageOutputImpl implements MessageOutput {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Gson gson;

    @Value("${kafka.producer.topic.notification}")
    private String notificationTopic;

    @Override
    public void sendFailMessage(UploadError uploadError) {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(notificationTopic, gson.toJson(uploadError));
        kafkaTemplate.send(producerRecord);
    }
}
