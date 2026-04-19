package com.baseorg.docassistant.service.qa;

import com.baseorg.docassistant.dto.qa.RetrievalCandidate;
import com.baseorg.docassistant.dto.qa.SearchResult;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 召回结果精排
 */
@Service
public class RerankService {

    public List<SearchResult.ResultItem> rerank(List<RetrievalCandidate> candidates, int topK) {
        return candidates.stream()
                .peek(candidate -> candidate.getItem().setScore(combineScore(candidate)))
                .sorted(Comparator.comparingDouble((RetrievalCandidate item) -> item.getItem().getScore()).reversed())
                .limit(topK)
                .map(RetrievalCandidate::getItem)
                .toList();
    }

    private double combineScore(RetrievalCandidate candidate) {
        double score = candidate.getVectorScore() * 0.65 + candidate.getKeywordScore() * 0.35;
        if (candidate.isTitleMatched()) {
            score += 0.05;
        }
        if (candidate.isSectionMatched()) {
            score += 0.03;
        }
        return Math.min(score, 0.99);
    }
}
