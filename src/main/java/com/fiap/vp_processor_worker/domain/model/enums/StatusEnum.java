package com.fiap.vp_processor_worker.domain.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StatusEnum {
    PROCESSING("processing", "processando"),
    PROCESSED("processed", "processado"),
    PROCESSING_ERROR("processing error", "erro no processamento"),
    ;
    private final String value;
    private final String description;
}
