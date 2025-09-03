package org.crumb.be.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "course_spots",
        indexes = @Index(name="idx_course_spots_course", columnList="courseId, createdAt"))
public class CourseSpot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private Long courseId;  // FK 컬럼
    @Column(nullable=false)
    private Long bakeryId;
    @Column(length=200)
    private String note;

    @CreationTimestamp @Column(nullable=false)
    private Instant createdAt;

    @Builder
    private CourseSpot(Long courseId, Long bakeryId, String note) {
        this.courseId = courseId;
        this.bakeryId = bakeryId;
        this.note = note;
    }

    public void changeNote(String note) { this.note = note; }
}