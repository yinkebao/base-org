package com.baseorg.docassistant.service.qa;

import com.baseorg.docassistant.dto.qa.QAResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 回答后处理
 */
@Service
public class AnswerPostProcessor {

    public String applyCitationSummary(String answer, List<QAResponse.SourceChunk> sources) {
        if (sources == null || sources.isEmpty()) {
            return answer;
        }
        StringBuilder builder = new StringBuilder(answer == null ? "" : answer.trim());
        builder.append("\n\n参考来源：");
        for (int i = 0; i < sources.size(); i++) {
            QAResponse.SourceChunk source = sources.get(i);
            builder.append("\n[")
                    .append(i + 1)
                    .append("] ")
                    .append(source.getDocTitle());
        }
        return builder.toString();
    }

    public String generatePromptHash(String question, String context) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((question + "\n---\n" + context).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            return "sha256:unavailable";
        }
    }
}
