package org.crumb.be.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BakeryDto {
    private String id;           // provider+sourceId 조합
    private String name;
    private double latitude;
    private double longitude;
    private String address;
    private String provider;     // "KAKAO" | "GOOGLE"
    private Double rating;       // null 허용 (카카오 없음)
    private Integer userRatingsTotal;
    private Double distanceMeters; // 요청 좌표로부터 계산
    private String phone;        // 있으면
    private String placeUrl;     // 상세 URL (provider 별)
}
