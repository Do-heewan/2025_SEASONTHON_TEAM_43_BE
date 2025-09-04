package org.crumb.be.service;

import lombok.RequiredArgsConstructor;
import org.crumb.be.client.GoogleMapClient;
import org.crumb.be.client.KakaoMapClient;
import org.crumb.be.dto.BakeryDto;
import org.crumb.be.utils.GeoUtils;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BakerySearchService {
    private final KakaoMapClient kakao;
    private final GoogleMapClient google;

    public List<BakeryDto> search(double lat, double lng, int radius, Integer limit, String sort) {
        List<BakeryDto> kakaoResult = kakao.searchBakery(lat, lng, radius);
        List<BakeryDto> googleResult = google.searchBakery(lat, lng, radius);

        List<BakeryDto> merged = new ArrayList<>();
        merged.addAll(kakaoResult);
        merged.addAll(googleResult);

        // 거리 계산 / 중복 제거 로직 작성


        return merged;
    }

    private List<BakeryDto> dedupe(List<BakeryDto> items) {
        List<BakeryDto> result = new ArrayList<>();
        for (BakeryDto b : items) {
            String nameKey = normalizeName(b.getName());
            boolean exists = result.stream().anyMatch(x ->
                    normalizeName(x.getName()).equals(nameKey) &&
                            GeoUtils.haversineMeters(x.getLatitude(), x.getLongitude(), b.getLatitude(), b.getLongitude()) < 80
            );
            if (!exists) result.add(b);
        }
        return result;
    }

    private String normalizeName(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC);
        return n.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
