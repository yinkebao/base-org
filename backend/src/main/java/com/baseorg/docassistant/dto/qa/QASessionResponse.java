package com.baseorg.docassistant.dto.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QASessionResponse {
    private Long sessionId;
    private String title;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
