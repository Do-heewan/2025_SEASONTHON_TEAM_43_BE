package org.crumb.be.client;

import lombok.RequiredArgsConstructor;

import org.crumb.be.dto.BakeryDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KakaoMapClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.kakao.rest-api-key}")
    private String kakaoKey;

    public List<BakeryDto> searchBakery(double lat, double lng, int radiusMeters) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://dapi.kakao.com/v2/local/search/keyword.json")
                .queryParam("query", "빵집") // "빵집" 으로 검색
                .queryParam("x", lng)
                .queryParam("y", lat)
                .queryParam("radius", Math.min(radiusMeters, 20000))
                .queryParam("size", 15)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null) return List.of();

        List<Map<String, Object>> docs = (List<Map<String, Object>>) body.getOrDefault("documents", List.of());

        return docs.stream().map(doc -> BakeryDto.builder()
                .id("KAKAO_" + doc.get("id"))
                .name((String) doc.get("place_name"))
                .address((String) doc.get("road_address_name"))
                .latitude(Double.parseDouble((String) doc.get("y")))
                .longitude(Double.parseDouble((String) doc.get("x")))
                .provider("KAKAO")
                .phone((String) doc.get("phone"))
                .placeUrl((String) doc.get("place_url"))
                .build()
        ).toList();
    }
}
