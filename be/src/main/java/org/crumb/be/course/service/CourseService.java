package org.crumb.be.course.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.crumb.be.common.exception.*;
import org.crumb.be.course.entity.Course;
import org.crumb.be.course.entity.CourseSpot;
import org.crumb.be.course.repository.CourseRepository;
import org.crumb.be.course.repository.CourseSpotRepository;
import org.crumb.be.course.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CourseService {

    private static final int MAX_SPOTS = 20;

    private final CourseRepository courseRepository;
    private final CourseSpotRepository courseSpotRepository;

    private Course getCourseOrThrow(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("course not found: " + id));
    }

    private static void assertOwner(Long me, Course c) {
        if (!Objects.equals(me, c.getAuthorId()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "only author can modify");
    }

    // 코스 생성
    @Transactional
    public CourseResponse create(Long me, CreateCourseRequest req) {
        var course = courseRepository.save(
                Course.builder()
                        .authorId(me)
                        .title(req.title())
                        .description(req.description())
                        .isPublic(Boolean.TRUE.equals(req.isPublic()))
                        .build()
        );

        if (req.bakeryIds() != null && !req.bakeryIds().isEmpty()) {
            var ids = req.bakeryIds().stream().distinct().toList();
            if (ids.size() > MAX_SPOTS)
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "too many spots");

            for (Long bid : ids) {
                courseSpotRepository.save(
                        CourseSpot.builder()
                                .courseId(course.getId())
                                .bakeryId(bid)
                                .build()
                );
            }
        }
        return toResponse(course.getId());
    }

    // 코스 수정
    @Transactional
    public CourseResponse update(Long me, Long courseId, UpdateCourseRequest req) {
        var c = getCourseOrThrow(courseId);
        assertOwner(me, c);
        c.updateMeta(req.title(), req.description(), req.isPublic());
        return toResponse(courseId);
    }

    // 코스에 빵집 추가
    @Transactional
    public CourseResponse addSpot(Long me, Long courseId, AddCourseSpotRequest req) {
        var c = getCourseOrThrow(courseId);
        assertOwner(me, c);

        if (courseSpotRepository.existsByCourseIdAndBakeryId(courseId, req.bakeryId()))
            throw new BusinessException(ErrorCode.CONFLICT, "bakery already added");

        long count = courseSpotRepository.countByCourseId(courseId);
        if (count >= MAX_SPOTS)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "max 20 spots");

        courseSpotRepository.save(
                CourseSpot.builder()
                        .courseId(courseId)
                        .bakeryId(req.bakeryId())
                        .note(req.note())
                        .build()
        );
        return toResponse(courseId);
    }

    // 모든 공개 코스 조회 (로그인 사용자 코스 제외)
    @Transactional
    public List<CourseResponse> listPublicExcludeMe(Long me) {
        var list = courseRepository.findAllByIsPublicTrueAndAuthorIdNotOrderByCreatedAtDesc(me);
        return list.stream().map(c -> toResponse(c.getId())).toList();
    }

    // 내 코스 조회 (공개/비공개 포함)
    @Transactional
    public List<CourseResponse> listMine(Long me) {
        var list = courseRepository.findAllByAuthorIdOrderByCreatedAtDesc(me);
        return list.stream().map(c -> toResponse(c.getId())).toList();
    }

    // 코스 상세 조회
    @Transactional
    public CourseResponse getDetail(Long me, Long id) {
        var c = getCourseOrThrow(id);
        if (!c.isPublic() && !Objects.equals(me, c.getAuthorId()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "forbidden");
        return toResponse(id);
    }

    // 코스 조회수 증가
    @Transactional
    public ViewCountResponse increaseView(Long me, Long id) {
        var c = getCourseOrThrow(id);
        // 공개 코스가 아니면 조회수 증가 불가
        if (!c.isPublic()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only public course can increase view");
        }
        c.increaseViewCount();
        return new ViewCountResponse(c.getViewCount());
    }

    private CourseResponse toResponse(Long id) {
        var c = getCourseOrThrow(id);
        var spots = courseSpotRepository.findByCourseIdOrderByCreatedAtAsc(id).stream()
                .map(s -> new CourseSpotResponse(s.getId(), s.getBakeryId(), s.getNote(), s.getCreatedAt()))
                .toList();

        return new CourseResponse(
                c.getId(), c.getTitle(), c.getDescription(), c.isPublic(),
                c.getViewCount(), c.getCreatedAt(), c.getUpdatedAt(), spots
        );
    }
}
