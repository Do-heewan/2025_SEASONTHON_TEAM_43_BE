package org.crumb.be.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import org.crumb.be.dto.KakaoBakeryDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KakaoMapClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.kakao.rest-api-key}")
    private String kakaoKey;
    private String searchUrl = "https://dapi.kakao.com/v2/local/search/keyword.json";

    public List<KakaoBakeryDto> searchBakery(double lat, double lng, int radiusMeters) {
        String query = "bakery";

        String url = UriComponentsBuilder
                .fromHttpUrl(searchUrl)
                .queryParam("query", query)
                .queryParam("x", lng)
                .queryParam("y", lat)
                .queryParam("radius", radiusMeters)
                .queryParam("size", 15)
                .toUriString();
        System.out.println("url: " + url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoKey);
        headers.set(HttpHeaders.ACCEPT, "application/json;charset=UTF-8");
        headers.set(HttpHeaders.ACCEPT_CHARSET, "UTF-8");

        System.out.println("Authorization: KakaoAK " + kakaoKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            System.out.println("Raw Kakao Response: " + response.getBody());
            System.out.println("Status: " + response.getStatusCode());

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode documents = root.path("documents");

            if (documents.isMissingNode() || !documents.isArray()) {
                return Collections.emptyList();
            }

            List<KakaoBakeryDto> result = new ArrayList<>();
            for (JsonNode doc : documents) {
                KakaoBakeryDto dto = new KakaoBakeryDto();
                dto.setId(doc.path("id").asLong());
                dto.setName(doc.path("place_name").asText());
                dto.setAddress(doc.path("address_name").asText());
                dto.setRoad_address(doc.path("road_address_name").asText());
                dto.setPhone(doc.path("phone").asText());
                dto.setLongitude(doc.path("x").asDouble());
                dto.setLatitude(doc.path("y").asDouble());
                dto.setDistance(doc.path("distance").asLong());
                dto.setPlace_url(doc.path("place_url").asText());
                result.add(dto);
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
