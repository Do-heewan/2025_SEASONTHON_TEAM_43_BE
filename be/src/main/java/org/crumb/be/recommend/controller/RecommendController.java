package org.crumb.be.recommend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.crumb.be.common.response.ApiResponse;
import org.crumb.be.recommend.dto.RecommendBakeryResponse;
import org.crumb.be.recommend.service.RecommendService;
import org.crumb.be.user.service.KakaoLoginService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="Recommend", description="위치+검색기록 기반 빵집 추천")
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;
    private final KakaoLoginService kakaoLoginService;

//    private static Long me(String header) {
//        if (header == null) throw new IllegalStateException("missing X-User-Id");
//        return Long.parseLong(header);
//    }

    @Operation(summary="주변 빵집 추천(반경/개수 고정: 5km/10개)")
    @GetMapping("/bakeries")
    public ApiResponse<List<RecommendBakeryResponse>> recommend(
            @RequestHeader("Authorization") String authorization,
            @RequestParam double lat,
            @RequestParam double lng
    ) {
        String token = authorization.replace("Bearer ", "").trim();
        Long userId = kakaoLoginService.getUserInfo(token).getId();

        return ApiResponse.ok(recommendService.recommend(userId, lat, lng));
    }
}
