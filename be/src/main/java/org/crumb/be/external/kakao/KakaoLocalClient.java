package org.crumb.be.external.kakao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class KakaoLocalClient {

    private final WebClient kakaoClient;

    public Mono<KakaoSearchResponse> keywordSearch(
            String query, Double lat, Double lng, Integer radius, Integer size
    ) {
        final int sz = (size == null ? 10 : Math.min(size, 15));

        return kakaoClient.get()
                .uri(uri -> {
                    var b = uri.path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("size", sz);
                    if (lat != null && lng != null) {
                        b.queryParam("y", lat).queryParam("x", lng);
                        if (radius != null) b.queryParam("radius", radius);
                        b.queryParam("sort", "distance");
                    }
                    return b.build();
                })
                .retrieve()
                .bodyToMono(KakaoSearchResponse.class);
    }
}
