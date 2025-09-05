package org.crumb.be.external.kakao;

import java.util.List;

public record KakaoSearchResponse(
        List<Document> documents, Meta meta
) {
    public record Document(
            String id,
            String place_name,
            String address_name,
            String road_address_name,
            String x, // longitude
            String y, // latitude
            String distance // meters (string)
    ) {}
    public record Meta(Integer total_count, Integer pageable_count, Boolean is_end) {}
}
