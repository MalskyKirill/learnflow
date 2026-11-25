package ru.mephi.learnflow.service;

import ru.mephi.learnflow.entity.Enrollment;
import ru.mephi.learnflow.entity.EnrollmentStatus;

import java.time.Instant;

public record EnrollmentDetails(Long id, Long studentId, Long courseId, Instant enrolledAt, EnrollmentStatus status) {

    static EnrollmentDetails from(Enrollment enrollment) {
        return new EnrollmentDetails(enrollment.getId(), enrollment.getStudent().getId(),
                enrollment.getCourse().getId(), enrollment.getEnrolledAt(), enrollment.getStatus());
    }
}
