package com.studyhub.concept;

import com.studyhub.course.Course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.studyhub.concept.ConceptStatus;
import org.hibernate.annotations.CreationTimestamp;
import java.sql.Timestamp;

@Entity
@Table(
    name = "concept_dependencies",
    uniqueConstraints = @UniqueConstraint(columnNames = {"concept_id", "prerequisite_id"})
)
public class ConceptDependency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite_id", nullable = false)
    private Concept prerequisiteConcept;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id", nullable = false)
    private Concept dependentConcept;

    @CreationTimestamp
    private Timestamp createdAt;

    protected ConceptDependency() {}

    public ConceptDependency(Concept prerequisiteConcept, Concept dependentConcept) {
        this.prerequisiteConcept = prerequisiteConcept;
        this.dependentConcept = dependentConcept;
    }

    public Long getId() {
        return id;
    }

    public Concept getPrerequisiteConcept() {
        return prerequisiteConcept;
    }

    public Concept getDependentConcept() {
        return dependentConcept;
    }
}