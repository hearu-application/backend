package com.example.hearu.ai.character.domain;

import com.example.hearu.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Companion extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "companion_id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Column(name = "persona", nullable = false, columnDefinition = "TEXT")
    private String persona;

    @Column(name = "behavior_rules", nullable = false, columnDefinition = "TEXT")
    private String behaviorRules;

    @Column(name = "examples", nullable = false, columnDefinition = "TEXT")
    private String examples;

    private Companion(
        String name,
        String description,
        String persona,
        String behaviorRules,
        String examples
    ) {
        this.name =  name;
        this.description =  description;
        this.persona = persona;
        this.behaviorRules = behaviorRules;
        this.examples = examples;
    }

    public static Companion create(
        String name,
        String description,
        String persona,
        String behaviorRules,
        String examples
    ) {
        return new Companion(
            name,
            description,
            persona,
            behaviorRules,
            examples
        );
    }
}
