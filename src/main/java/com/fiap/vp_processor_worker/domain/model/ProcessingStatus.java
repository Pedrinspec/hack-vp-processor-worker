package com.fiap.vp_processor_worker.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingStatus {
    private String status;
    private int lastSecondProcessed;
    private long updatedAt;
}
