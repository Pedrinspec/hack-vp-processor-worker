package com.fiap.vp_processor_worker.domain.service.impl;

import com.fiap.vp_processor_worker.application.ports.output.MessageOutput;
import com.fiap.vp_processor_worker.application.ports.output.S3Output;
import com.fiap.vp_processor_worker.domain.exceptions.NoSuchVideoException;
import com.fiap.vp_processor_worker.domain.model.StatusUpdate;
import com.fiap.vp_processor_worker.domain.model.enums.StatusEnum;
import com.fiap.vp_processor_worker.domain.service.model.ProcessRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoProcessorServiceImplTest {

    @Mock
    private S3Output s3Output;

    @Mock
    private MessageOutput messageOutput;

    @InjectMocks
    private VideoProcessorServiceImpl service;

    @Test
    void shouldProcessVideoAndSendStatusUpdatesInOrderWhenVideoExists() {
        // Arrange
        UUID uploadId = UUID.randomUUID();
        String key = "videos/video.mp4";
        ProcessRequest processRequest = new ProcessRequest(uploadId, key);

        when(s3Output.exists(key)).thenReturn(true);

        // Act
        service.execute(processRequest);

        // Assert
        InOrder inOrder = inOrder(s3Output, messageOutput);
        inOrder.verify(s3Output).exists(key);
        inOrder.verify(messageOutput).sendStatusUpdate(anyStatusUpdateWith(uploadId, StatusEnum.PROCESSING));
        inOrder.verify(s3Output).generateFramesAndZipToS3(uploadId, key);
        inOrder.verify(messageOutput).sendStatusUpdate(anyStatusUpdateWith(uploadId, StatusEnum.PROCESSED));
        verifyNoMoreInteractions(messageOutput, s3Output);
    }

    @Test
    void shouldThrowExceptionWhenVideoDoesNotExist() {
        // Arrange
        UUID uploadId = UUID.randomUUID();
        String key = "videos/missing.mp4";
        ProcessRequest processRequest = new ProcessRequest(uploadId, key);

        when(s3Output.exists(key)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.execute(processRequest))
                .isInstanceOf(NoSuchVideoException.class)
                .hasMessageContaining(uploadId.toString())
                .hasMessageContaining(key);

        verify(s3Output).exists(key);
        verify(messageOutput).sendStatusUpdate(anyStatusUpdateWith(uploadId, StatusEnum.PROCESSING_ERROR));
        verify(s3Output, never()).generateFramesAndZipToS3(any(), any());
        verifyNoMoreInteractions(messageOutput, s3Output);
    }

    @Test
    void shouldNotSendProcessedStatusWhenFrameGenerationFails() {
        // Arrange
        UUID uploadId = UUID.randomUUID();
        String key = "videos/error.mp4";
        ProcessRequest processRequest = new ProcessRequest(uploadId, key);

        when(s3Output.exists(key)).thenReturn(true);
        RuntimeException processingException = new RuntimeException("zip generation failed");
        doThrow(processingException).when(s3Output).generateFramesAndZipToS3(uploadId, key);

        // Act & Assert
        assertThatThrownBy(() -> service.execute(processRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("zip generation failed");

        verify(s3Output).exists(key);
        verify(messageOutput).sendStatusUpdate(anyStatusUpdateWith(uploadId, StatusEnum.PROCESSING));
        verify(s3Output).generateFramesAndZipToS3(uploadId, key);
        verify(messageOutput, never()).sendStatusUpdate(anyStatusUpdateWith(uploadId, StatusEnum.PROCESSED));
        verifyNoMoreInteractions(messageOutput, s3Output);
    }

    @Test
    void shouldHandleNullKeyWhenVideoDoesNotExist() {
        // Arrange
        UUID uploadId = UUID.randomUUID();
        ProcessRequest processRequest = new ProcessRequest(uploadId, null);

        when(s3Output.exists(null)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.execute(processRequest))
                .isInstanceOf(NoSuchVideoException.class)
                .hasMessageContaining(uploadId.toString())
                .hasMessageContaining("null");

        verify(s3Output).exists(null);
        verify(messageOutput).sendStatusUpdate(anyStatusUpdateWith(uploadId, StatusEnum.PROCESSING_ERROR));
        verify(s3Output, never()).generateFramesAndZipToS3(any(), any());
        verifyNoMoreInteractions(messageOutput, s3Output);
    }

    private StatusUpdate anyStatusUpdateWith(UUID uploadId, StatusEnum status) {
        return argThat(statusUpdate -> statusUpdate != null
                && uploadId.equals(statusUpdate.getUploadId())
                && status == statusUpdate.getStatus());
    }
}
