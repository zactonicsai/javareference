package com.example.legaldoc.controller;

import com.example.legaldoc.service.S3Service;
import com.example.legaldoc.service.SqsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class UploadControllerTest {

    @Test
    void uploadSucceedsAndReturnsDocumentId() throws Exception {
        S3Service s3 = Mockito.mock(S3Service.class);
        SqsService sqs = Mockito.mock(SqsService.class);

        when(s3.uploadFile(anyString(), any())).thenReturn("some-key");
        when(sqs.sendMessage(any())).thenReturn("msg-123");

        UploadController controller = new UploadController(s3, sqs);
        MockMvc mockMvc = standaloneSetup(controller).build();

        MockMultipartFile file = new MockMultipartFile(
                "file", "evidence.txt", "text/plain",
                "homicide evidence from the crime scene".getBytes());

        mockMvc.perform(multipart("/api/upload")
                        .file(file)
                        .param("subject", "Case #2025-001")
                        .param("latitude", "33.749")
                        .param("longitude", "-84.388")
                        .param("locationDescription", "Atlanta")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentId").exists())
                .andExpect(jsonPath("$.sqsMessageId").value("msg-123"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void uploadFailsWithoutSubject() throws Exception {
        S3Service s3 = Mockito.mock(S3Service.class);
        SqsService sqs = Mockito.mock(SqsService.class);

        UploadController controller = new UploadController(s3, sqs);
        MockMvc mockMvc = standaloneSetup(controller).build();

        MockMultipartFile file = new MockMultipartFile(
                "file", "evidence.txt", "text/plain", "data".getBytes());

        mockMvc.perform(multipart("/api/upload")
                        .file(file)
                        .param("subject", "")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFailsWithEmptyFile() throws Exception {
        S3Service s3 = Mockito.mock(S3Service.class);
        SqsService sqs = Mockito.mock(SqsService.class);

        UploadController controller = new UploadController(s3, sqs);
        MockMvc mockMvc = standaloneSetup(controller).build();

        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/upload")
                        .file(empty)
                        .param("subject", "Case X")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
    }
}
