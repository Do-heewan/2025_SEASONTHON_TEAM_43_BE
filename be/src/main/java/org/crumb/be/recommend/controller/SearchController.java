package org.crumb.be.recommend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.crumb.be.common.response.ApiResponse;
import org.crumb.be.recommend.dto.SearchRequest;
import org.crumb.be.recommend.service.SearchService;
import org.crumb.be.user.service.KakaoLoginService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Tag(name="Bakery Search", description="카카오 로컬 키워드 기반 빵집 검색")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final KakaoLoginService kakaoLoginService;

//    private static Long me(String header) {
//        if (header == null) throw new IllegalStateException("missing X-User-Id");
//        return Long.parseLong(header);
//    }

    @Operation(summary = "빵집 검색(검색 이력 저장)")
    @GetMapping("/bakeries")
    public Mono<ApiResponse<List<SearchService.BakerySearchResult>>> search(
            @RequestHeader("Authorization") String authorization,
            @Valid @ModelAttribute SearchRequest req
    ) {
        String token = authorization.replace("Bearer ", "").trim();
        Long userId = kakaoLoginService.getUserInfo(token).getId();

        return searchService.searchAndLog(userId, req)
                .map(ApiResponse::ok);
    }
}
