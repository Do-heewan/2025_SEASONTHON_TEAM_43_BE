package org.crumb.be.course.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.crumb.be.common.response.ApiResponse;
import org.crumb.be.course.service.CourseService;
import org.crumb.be.course.dto.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Course", description = "빵지순례 코스 관리 API")
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /** 로그인 붙이기 전까지 임시 사용자 ID */
    private static Long me(String header) {
        if (header == null) throw new IllegalStateException("missing X-User-Id");
        return Long.parseLong(header);
    }

    @Operation(summary = "코스 생성")
    @PostMapping
    public ApiResponse<CourseResponse> create(
            @RequestHeader(name="X-User-Id", required=false) String userId,
            @Valid @RequestBody CreateCourseRequest req) {
        return ApiResponse.ok(courseService.create(me(userId), req));
    }

    @Operation(summary = "코스 수정(제목/설명/공개여부)(일부 수정 가능)")
    @PutMapping("/{courseId}")
    public ApiResponse<CourseResponse> update(
            @RequestHeader(name="X-User-Id", required=false) String userId,
            @PathVariable Long courseId,
            @Valid @RequestBody UpdateCourseRequest req) {
        return ApiResponse.ok(courseService.update(me(userId), courseId, req));
    }

    @Operation(summary = "코스에 빵집 추가")
    @PostMapping("/{courseId}/spots")
    public ApiResponse<CourseResponse> addSpot(
            @RequestHeader(name="X-User-Id", required=false) String userId,
            @PathVariable Long courseId,
            @Valid @RequestBody AddCourseSpotRequest req) {
        return ApiResponse.ok(courseService.addSpot(me(userId), courseId, req));
    }

    // 모든 공개 코스 조회
    @Operation(summary = "모든 공개 코스 조회(로그인 사용자 코스 제외)")
    @GetMapping("/public")
    public ApiResponse<List<CourseResponse>> listPublicExcludeMe(
            @RequestHeader(name="X-User-Id", required=false) String userId) {
        Long me = (userId==null ? -1L : Long.parseLong(userId));
        return ApiResponse.ok(courseService.listPublicExcludeMe(me));
    }

    // 내 코스 조회
    @Operation(summary = "내 코스 전체 조회 (공개/비공개 포함)")
    @GetMapping("/me")
    public ApiResponse<List<CourseResponse>> listMine(
            @RequestHeader(name="X-User-Id", required=false) String userId) {
        return ApiResponse.ok(courseService.listMine(me(userId)));
    }

    // 코스 상세 조회
    @Operation(summary = "코스 상세 조회")
    @GetMapping("/{courseId}")
    public ApiResponse<CourseResponse> getDetail(
            @RequestHeader(name="X-User-Id", required=false) String userId,
            @PathVariable Long courseId) {
        Long me = (userId==null ? -1L : Long.parseLong(userId));
        return ApiResponse.ok(courseService.getDetail(me, courseId));
    }

    // 코스 조회수 증가
    @Operation(summary = "코스 조회수 증가(공개 코스만 가능)")
    @PostMapping("/{courseId}/views")
    public ApiResponse<ViewCountResponse> increaseView(
            @RequestHeader(name="X-User-Id", required=false) String userId,
            @PathVariable Long courseId) {
        Long me = (userId==null ? -1L : Long.parseLong(userId));
        return ApiResponse.ok(courseService.increaseView(me, courseId));
    }
}
