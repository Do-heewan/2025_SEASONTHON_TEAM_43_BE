package org.crumb.be.recommend.dto;

public record RecommendBakeryResponse(
        Long id,
        String name,
        String address,
        Double lat,
        Double lng,
        String intro,
        Double distance, // meters
        String thumbnailUrl
) {
    public static RecommendBakeryResponse from(RecommendedBakery i) {
        return new RecommendBakeryResponse(i.id(), i.name(), i.address(), i.lat(), i.lng(), i.intro(), i.distance(), i.thumbnailUrl());
    }
}
