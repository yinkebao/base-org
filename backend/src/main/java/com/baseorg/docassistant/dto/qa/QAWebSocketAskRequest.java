package com.baseorg.docassistant.dto.qa;

import lombok.Data;

@Data
public class QAWebSocketAskRequest {
    private String type;
    private Long sessionId;
    private String messageId;
    private String question;
    private Boolean webSearchEnabled;
}
