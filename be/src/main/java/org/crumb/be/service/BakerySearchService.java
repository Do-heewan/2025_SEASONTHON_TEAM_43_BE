package org.crumb.be.service;

import lombok.RequiredArgsConstructor;
import org.crumb.be.client.KakaoMapClient;
import org.crumb.be.dto.KakaoBakeryDto;
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
//    private final GoogleMapClient google;

    public List<KakaoBakeryDto> search(double lat, double lng, int radius, int size) {
        List<KakaoBakeryDto> kakaoResult = kakao.searchBakery(lat, lng, radius);
        System.out.println("kakaoResult: " + kakaoResult);

        // 거리 계산 / 중복 제거 로직 작성
        // 중복 제거
        List<KakaoBakeryDto> deduped = dedupe(kakaoResult);

        // 거리 계산 후 정렬
        List<KakaoBakeryDto> sorted = deduped.stream()
                .sorted(Comparator.comparingDouble(b ->
                        GeoUtils.haversineMeters(lat, lng, b.getLatitude(), b.getLongitude())))
                .collect(Collectors.toList());

        // size만큼 자르기
        if (sorted.size() > size) {
            return sorted.subList(0, size);
        } else {
            return sorted;
        }
    }

    private List<KakaoBakeryDto> dedupe(List<KakaoBakeryDto> items) {
        List<KakaoBakeryDto> result = new ArrayList<>();
        for (KakaoBakeryDto b : items) {
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
