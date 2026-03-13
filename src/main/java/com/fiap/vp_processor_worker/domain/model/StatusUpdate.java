package com.fiap.vp_processor_worker.domain.model;

import com.fiap.vp_processor_worker.domain.model.enums.StatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class StatusUpdate {
    private UUID uploadId;
    private StatusEnum status;
}
