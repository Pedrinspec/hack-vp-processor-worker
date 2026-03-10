package com.fiap.vp_processor_worker.infrastructure.adapter.output;

import com.fiap.vp_processor_worker.application.ports.output.CacheOutput;
import com.fiap.vp_processor_worker.domain.model.ProcessingStatus;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisOutputImpl implements CacheOutput {

    private final RedisTemplate<String, String> redisTemplate;
    private final Gson gson;

    @Override
    public void save(UUID uploadId, ProcessingStatus status) {
        redisTemplate.opsForValue().set(
                key(uploadId),
                gson.toJson(status)
        );
    }

    @Override
    public ProcessingStatus get(UUID uploadId) {
        String json = redisTemplate.opsForValue().get(key(uploadId));

        if (json == null)
            return null;

        return gson.fromJson(json, ProcessingStatus.class);
    }

    private String key(UUID uploadId) {
        return "processor:job:" + uploadId.toString();
    }
}
