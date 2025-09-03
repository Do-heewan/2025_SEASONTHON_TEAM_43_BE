package org.crumb.be.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "courses")
public class Course {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false) private Long authorId;
    @Column(nullable=false, length=200) private String title;
    @Column(columnDefinition="text") private String description;

    @Column(nullable=false) private boolean isPublic = false;

    @Column(nullable=false) private long viewCount = 0L;

    @CreationTimestamp @Column(nullable=false)
    private Instant createdAt;
    @UpdateTimestamp  @Column(nullable=false)
    private Instant updatedAt;

    @Builder
    private Course(Long authorId, String title, String description, boolean isPublic) {
        this.authorId = authorId;
        this.title = title;
        this.description = description;
        this.isPublic = isPublic;
    }

    public void updateMeta(String title, String description, Boolean isPublic) {
        if (title != null && !title.isBlank()) this.title = title;
        if (description != null) this.description = description;
        if (isPublic != null) this.isPublic = isPublic;
    }

    /** 조회수 증가 도메인 메서드 */
    public void increaseViewCount() {
        this.viewCount++;
    }
}
