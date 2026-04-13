package com.baseorg.docassistant.controller;

import com.baseorg.docassistant.dto.importtask.CreateImportTaskRequest;
import com.baseorg.docassistant.dto.importtask.ImportTaskResponse;
import com.baseorg.docassistant.exception.GlobalExceptionHandler;
import com.baseorg.docassistant.service.ImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportControllerTest {

    private ImportService importService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        importService = mock(ImportService.class);
        ImportController controller = new ImportController(importService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalLongResolver())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldCreateTaskWithMultipartRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "spec.md", "text/markdown", "# 标题".getBytes());
        when(importService.createTask(any(CreateImportTaskRequest.class), eq(8L))).thenReturn(
                ImportTaskResponse.builder()
                        .taskId("task-001")
                        .filename("spec.md")
                        .fileType("md")
                        .status("PENDING")
                        .progress(0)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        mockMvc.perform(multipart("/api/v1/documents/import")
                        .file(file)
                        .param("sensitivity", "INTERNAL")
                        .param("versionLabel", "v1.0.0")
                        .param("tags", "研发规范", "接口")
                        .param("chunkSize", "500")
                        .param("chunkOverlap", "100")
                        .param("structuredChunk", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.taskId").value("task-001"));

        ArgumentCaptor<CreateImportTaskRequest> captor = ArgumentCaptor.forClass(CreateImportTaskRequest.class);
        verify(importService).createTask(captor.capture(), eq(8L));
        CreateImportTaskRequest request = captor.getValue();
        assertThat(request.getVersionLabel()).isEqualTo("v1.0.0");
        assertThat(request.getTags()).containsExactly("研发规范", "接口");
        assertThat(request.getChunkSize()).isEqualTo(500);
        assertThat(request.getChunkOverlap()).isEqualTo(100);
        assertThat(request.getStructuredChunk()).isTrue();
    }

    @Test
    void shouldRejectJsonRequestForImport() throws Exception {
        mockMvc.perform(post("/api/v1/documents/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file\":\"bad\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("导入请求必须使用 multipart/form-data"));
    }

    @Test
    void shouldReturnValidationErrorWhenFileMissing() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents/import")
                        .param("sensitivity", "INTERNAL")
                        .param("versionLabel", "v1.0.0")
                        .param("chunkSize", "500")
                        .param("chunkOverlap", "100")
                        .param("structuredChunk", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.data.file").value("文件不能为空"));
    }

    private static class AuthenticationPrincipalLongResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && Long.class.equals(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest,
                                      org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return 8L;
        }
    }
}
