package org.crumb.be.recommend.service;

import lombok.RequiredArgsConstructor;
import org.crumb.be.external.kakao.KakaoLocalClient;
import org.crumb.be.external.kakao.KakaoSearchResponse;
import org.crumb.be.recommend.dto.SearchRequest;
import org.crumb.be.recommend.entity.SearchHistory;
import org.crumb.be.recommend.repository.SearchHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final KakaoLocalClient kakaoLocalClient;
    private final SearchHistoryRepository searchHistoryRepository;

    @Transactional
    public Mono<List<BakerySearchResult>> searchAndLog(Long userId, SearchRequest req) {
        // 1) Kakao 검색
        Mono<KakaoSearchResponse> mono = kakaoLocalClient.keywordSearch(
                req.query(), req.lat(), req.lng(),
                req.radius()==null? 3000 : req.radius(), // 기본 3km
                15
        );

        // 2) 결과 map + 3) 검색 이력 저장
        return mono.map(resp -> {
            var list = resp.documents() == null ? List.<BakerySearchResult>of() :
                    resp.documents().stream().map(d ->
                            new BakerySearchResult(
                                    d.id(),
                                    d.place_name(),
                                    (d.road_address_name()!=null && !d.road_address_name().isBlank())
                                            ? d.road_address_name() : d.address_name(),
                                    safeDouble(d.y()), safeDouble(d.x()),
                                    safeInt(d.distance())
                            )
                    ).toList();

            // 비동기 저장 (실패해도 검색은 응답)
            searchHistoryRepository.save(
                    SearchHistory.builder()
                            .userId(userId)
                            .query(req.query())
                            .lat(req.lat()).lng(req.lng())
                            .build()
            );

            return list;
        });
    }

    private static Double safeDouble(String s) {
        try { return s==null? null : Double.parseDouble(s); } catch (Exception e){ return null; }
    }
    private static Integer safeInt(String s) {
        try { return s==null? null : Integer.parseInt(s); } catch (Exception e){ return null; }
    }

    public record BakerySearchResult(
            String placeId, String name, String address,
            Double lat, Double lng, Integer distance
    ) {}
}
