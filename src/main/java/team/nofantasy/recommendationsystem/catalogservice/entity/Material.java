package team.nofantasy.recommendationsystem.catalogservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * One educational material. For a paid material {@code summary} holds only a short benefit, never its content.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "material")
public class Material {
    @Setter(AccessLevel.NONE)
    @Id
    @Size(max = 64)
    private String id;

    @NotBlank
    @Size(max = 300)
    private String title;

    private String summary;

    @Size(max = 32)
    private String format;

    private Integer durationMinutes;

    @Size(max = 16)
    private String level;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> topics = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> businessStages = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> spheres = new ArrayList<>();

    @NotNull
    private Boolean paid;

    @NotBlank
    @Size(max = 1000)
    private String url;
}
