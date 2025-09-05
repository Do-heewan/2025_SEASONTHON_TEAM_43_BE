package org.crumb.be.recommend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.crumb.be.common.response.ApiResponse;
import org.crumb.be.recommend.dto.RecommendBakeryResponse;
import org.crumb.be.recommend.service.RecommendService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="Recommend", description="위치+검색기록 기반 빵집 추천")
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    private static Long me(String header) {
        if (header == null) throw new IllegalStateException("missing X-User-Id");
        return Long.parseLong(header);
    }

    @Operation(summary="주변 빵집 추천(반경/개수 고정: 5km/10개)")
    @GetMapping("/bakeries")
    public ApiResponse<List<RecommendBakeryResponse>> recommend(
            @RequestHeader(name="X-User-Id", required=false) String userId,
            @RequestParam double lat,
            @RequestParam double lng
    ) {
        return ApiResponse.ok(recommendService.recommend(me(userId), lat, lng));
    }
}
