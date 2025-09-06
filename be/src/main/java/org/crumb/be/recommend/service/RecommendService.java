package org.crumb.be.recommend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.crumb.be.common.exception.BusinessException;
import org.crumb.be.common.exception.ErrorCode;
import org.crumb.be.recommend.dto.RecommendBakeryResponse;
import org.crumb.be.recommend.dto.RecommendedBakery;
import org.crumb.be.recommend.entity.SearchHistory;
import org.crumb.be.recommend.repository.SearchHistoryRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final SearchHistoryRepository searchHistoryRepository;

    @Qualifier("fastapiClient")
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
        try {
            var uriSpec = fastapiClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path("/recommend")
                                .queryParam("lat", lat)
                                .queryParam("lng", lng);
                        if (!keywords.isEmpty()) {
                            // FastAPI는 콤마 기준 split → 콤마로 조인
                            b.queryParam("keywords", String.join(",", keywords));
                        }
                        return b.build();
                    })
                    .accept(MediaType.APPLICATION_JSON);

            var items = uriSpec.retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class).flatMap(body -> {
                                log.warn("[recommend] fastapi error status={} body={}", resp.statusCode(), body);
                                return Mono.error(new IllegalStateException("fastapi error: " + resp.statusCode()));
                            })
                    )
                    .bodyToFlux(RecommendedBakery.class)
                    .timeout(Duration.ofSeconds(10))
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(300))
                            .filter(ex -> !(ex instanceof WebClientResponseException)) // 4xx/5xx는 재시도 X
                    )
                    .collectList()
                    .block();

            if (items == null) return List.of();
            return items.stream()
                    .limit(limit)
                    .map(RecommendBakeryResponse::from)
                    .toList();
        } catch (Exception e) {
            log.error("[recommend] fastapi call failed (lat={}, lng={}, keywords={})", lat, lng, keywords, e);
            return List.of();
        }
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
