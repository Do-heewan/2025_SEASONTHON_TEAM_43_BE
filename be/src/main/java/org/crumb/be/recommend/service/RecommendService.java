package org.crumb.be.recommend.service;

import lombok.RequiredArgsConstructor;
import org.crumb.be.common.exception.BusinessException;
import org.crumb.be.common.exception.ErrorCode;
import org.crumb.be.recommend.dto.RecommendBakeryResponse;
import org.crumb.be.recommend.dto.RecommendedBakery;
import org.crumb.be.recommend.entity.SearchHistory;
import org.crumb.be.recommend.repository.SearchHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecommendService {

    private final SearchHistoryRepository searchHistoryRepository;
    private final WebClient fastapiClient;

    @Value("${recommend.limit:10}") private int limit;

    /** 위치(lat/lng) + 최근 검색어 기반 추천 */
    public List<RecommendBakeryResponse> recommend(Long userId, double lat, double lng) {
        // 1) 최근 검색 이력 가져와서 키워드 뽑기 (빈도 상위 3~5개 권장)
        var histories = searchHistoryRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
        var keywords = extractKeywords(histories, 5);
//        if (keywords.isEmpty()) {
//            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "검색 기록이 없어 추천할 수 없습니다.");
//        }

        // 2) FastAPI 호출 (exclude는 현재 미사용)
        var uriSpec = fastapiClient.get()
                .uri(uriBuilder -> {
                    var b = uriBuilder.path("/recommend")
                            .queryParam("lat", lat)
                            .queryParam("lng", lng);
                    if (!keywords.isEmpty()) {
                        b.queryParam("keywords", String.join(" ", keywords));
                    }
                    return b.build();
                })
                .accept(MediaType.APPLICATION_JSON);

        var items = uriSpec.retrieve()
                .bodyToFlux(RecommendedBakery.class)
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(300)))
                .collectList()
                .block();

        if (items == null) return List.of();
        return items.stream()
                .limit(limit)
                .map(RecommendBakeryResponse::from)
                .toList();
    }

    /** 최근 검색어에서 공백/빈문자 제거, 등장 빈도순 상위 N만 사용 */
    private List<String> extractKeywords(List<SearchHistory> histories, int topN) {
        Map<String, Integer> freq = new HashMap<>();
        for (SearchHistory h : histories) {
            var q = h.getQuery();
            if (q == null || q.isBlank()) continue;
            // 필요 시 여기서 형태소 분해/토큰 분리도 가능. MVP는 원문 단위.
            var key = q.trim();
            freq.put(key, freq.getOrDefault(key, 0) + 1);
        }
        return freq.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
    }
}
