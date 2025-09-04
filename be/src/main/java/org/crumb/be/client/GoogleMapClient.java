package org.crumb.be.client;

import lombok.RequiredArgsConstructor;

import org.crumb.be.dto.BakeryDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleMapClient {

    private final RestTemplate restTemplate;

    @Value("${app.google.api-key}")
    private String googleKey;

    public List<BakeryDto> searchBakery(double lat, double lng, int radiusMeters) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://maps.googleapis.com/maps/api/place/nearbysearch/json")
                .queryParam("location", lat + "," + lng)
                .queryParam("radius", Math.min(radiusMeters, 50000))
                .queryParam("type", "bakery")
                .queryParam("keyword", "bakery")
                .queryParam("language", "ko")
                .queryParam("key", googleKey)
                .toUriString();

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null) return List.of();

        List<Map<String, Object>> results = (List<Map<String, Object>>) body.getOrDefault("results", List.of());

        return results.stream().map(r -> {
            Map<String, Object> geometry = (Map<String, Object>) r.get("geometry");
            Map<String, Object> loc = (Map<String, Object>) geometry.get("location");

            return BakeryDto.builder()
                    .id("GOOGLE_" + r.get("place_id"))
                    .name((String) r.get("name"))
                    .address((String) r.get("vicinity"))
                    .latitude(((Number) loc.get("lat")).doubleValue())
                    .longitude(((Number) loc.get("lng")).doubleValue())
                    .provider("GOOGLE")
                    .rating(r.get("rating") != null ? ((Number) r.get("rating")).doubleValue() : null)
                    .userRatingsTotal(r.get("user_ratings_total") != null ? ((Number) r.get("user_ratings_total")).intValue() : null)
                    .placeUrl("https://maps.google.com/?cid=" + r.get("place_id"))
                    .build();
        }).toList();
    }
}
