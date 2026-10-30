package com.airtribe.learntrack.service;

import com.airtribe.learntrack.entity.Enrollment;
import com.airtribe.learntrack.exception.DuplicateEnrollmentException;
import com.airtribe.learntrack.exception.EntityNotFoundException;
import com.airtribe.learntrack.exception.InvalidInputException;
import com.airtribe.learntrack.util.IdGenerator;
import com.airtribe.learntrack.util.InputValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EnrollmentService {

    private final List<Enrollment> enrollments = new ArrayList<>();
    private final StudentService   studentService;
    private final CourseService    courseService;

    public EnrollmentService(StudentService studentService, CourseService courseService) {
        this.studentService = studentService;
        this.courseService  = courseService;
    }

    public Enrollment enroll(String studentId, String courseId) {
        InputValidator.requireNonBlank(studentId, "Student ID");
        InputValidator.requireNonBlank(courseId,  "Course ID");

        studentService.searchById(studentId.trim());  // throws if not found
        courseService.findById(courseId.trim());       // throws if not found

        boolean alreadyActive = enrollments.stream()
                .anyMatch(e -> e.getStudentId().equals(studentId.trim())
                            && e.getCourseId().equals(courseId.trim())
                            && e.isActive());
        if (alreadyActive) {
            throw new DuplicateEnrollmentException(studentId.trim(), courseId.trim());
        }

        String id = IdGenerator.getNextEnrollmentId();
        Enrollment enrollment = new Enrollment(id, studentId.trim(), courseId.trim());
        enrollments.add(enrollment);
        return enrollment;
    }

    public List<Enrollment> getEnrollmentsByStudent(String studentId) {
        studentService.searchById(studentId.trim()); // throws if not found
        return enrollments.stream()
                .filter(e -> e.getStudentId().equals(studentId.trim()))
                .collect(Collectors.toList());
    }

    public Enrollment markCompleted(String enrollmentId) {
        Enrollment enrollment = getActiveEnrollment(enrollmentId);
        enrollment.setStatus(Enrollment.Status.COMPLETED);
        return enrollment;
    }

    public Enrollment markCancelled(String enrollmentId) {
        Enrollment enrollment = getActiveEnrollment(enrollmentId);
        enrollment.setStatus(Enrollment.Status.CANCELLED);
        return enrollment;
    }

    public List<Enrollment> listEnrollments() {
        return new ArrayList<>(enrollments);
    }

    private Enrollment findById(String id) {
        InputValidator.requireNonBlank(id, "Enrollment ID");
        return enrollments.stream()
                .filter(e -> e.getId().equals(id.trim()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Enrollment", id.trim()));
    }

    private Enrollment getActiveEnrollment(String enrollmentId) {
        Enrollment enrollment = findById(enrollmentId);
        if (!enrollment.isActive()) {
            throw new InvalidInputException(
                    "Enrollment '" + enrollmentId + "' is not active (status: " + enrollment.getStatus() + ").");
        }
        return enrollment;
    }
}
