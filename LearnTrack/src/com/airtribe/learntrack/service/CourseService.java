package com.airtribe.learntrack.service;

import com.airtribe.learntrack.entity.Course;
import com.airtribe.learntrack.exception.EntityNotFoundException;
import com.airtribe.learntrack.exception.InvalidInputException;
import com.airtribe.learntrack.util.IdGenerator;
import com.airtribe.learntrack.util.InputValidator;

import java.util.ArrayList;
import java.util.List;

public class CourseService {

    private final List<Course> courses = new ArrayList<>();

    public Course addCourse(String courseName, String description, int durationInWeeks) {
        String validName = InputValidator.requireNonBlank(courseName,   "Course Name");
        String validDesc = InputValidator.requireNonBlank(description,  "Description");
        InputValidator.requirePositive(durationInWeeks, "Duration In Weeks");
        InputValidator.validateMaxLength(validDesc, "Description", 500);

        checkNameUnique(validName);

        String id = IdGenerator.getNextCourseId();
        Course course = new Course(id, validName, validDesc, durationInWeeks);
        courses.add(course);
        return course;
    }

    public List<Course> listCourses() {
        return new ArrayList<>(courses);
    }

    public Course findById(String id) {
        InputValidator.requireNonBlank(id, "Course ID");
        return courses.stream()
                .filter(c -> c.getId().equals(id.trim()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Course", id.trim()));
    }

    public Course activateCourse(String id) {
        Course course = findById(id);
        course.setActive(true);
        return course;
    }

    public Course deactivateCourse(String id) {
        Course course = findById(id);
        if (!course.isActive()) {
            throw new InvalidInputException("Course '" + id.trim() + "' is already inactive.");
        }
        course.setActive(false);
        return course;
    }

    public Course updateCourse(String id, String newName, String newDesc,
                               int newDuration, Boolean newActive) {
        Course course = findById(id);

        if (newName != null && !newName.isBlank()) {
            if (!newName.trim().equalsIgnoreCase(course.getCourseName())) {
                checkNameUnique(newName.trim());
            }
            course.setCourseName(newName.trim());
        }
        if (newDesc != null && !newDesc.isBlank()) {
            InputValidator.validateMaxLength(newDesc.trim(), "Description", 500);
            course.setDescription(newDesc.trim());
        }
        if (newDuration > 0) {
            course.setDurationInWeeks(newDuration);
        }
        if (newActive != null) {
            course.setActive(newActive);
        }
        return course;
    }

    private void checkNameUnique(String name) {
        courses.stream()
                .filter(c -> c.getCourseName().equalsIgnoreCase(name))
                .findFirst()
                .ifPresent(c -> {
                    throw new InvalidInputException("A course named '" + name + "' already exists.");
                });
    }
}
