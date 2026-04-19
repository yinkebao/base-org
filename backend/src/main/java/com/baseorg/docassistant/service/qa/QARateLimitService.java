package com.baseorg.docassistant.service.qa;

import com.baseorg.docassistant.exception.BusinessException;
import com.baseorg.docassistant.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户级限流
 */
@Service
public class QARateLimitService {

    private static final int LIMIT = 10;
    private static final long WINDOW_SECONDS = 60;

    private final Map<Long, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public void check(Long userId) {
        Deque<Instant> queue = buckets.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            Instant threshold = Instant.now().minusSeconds(WINDOW_SECONDS);
            while (!queue.isEmpty() && queue.peekFirst().isBefore(threshold)) {
                queue.pollFirst();
            }
            if (queue.size() >= LIMIT) {
                throw new BusinessException(ErrorCode.QA_RATE_LIMITED);
            }
            queue.addLast(Instant.now());
        }
    }
}
