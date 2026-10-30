package com.airtribe.learntrack.service;

import com.airtribe.learntrack.entity.Student;
import com.airtribe.learntrack.exception.EntityNotFoundException;
import com.airtribe.learntrack.exception.InvalidInputException;
import com.airtribe.learntrack.util.IdGenerator;
import com.airtribe.learntrack.util.InputValidator;

import java.util.ArrayList;
import java.util.List;

public class StudentService {

    private final List<Student> students = new ArrayList<>();

    public Student addStudent(String firstName, String lastName,
                              String email, String phone, String batch) {
        String validFirst = InputValidator.requireNonBlank(firstName, "First Name");
        String validLast  = InputValidator.requireNonBlank(lastName,  "Last Name");
        String validBatch = InputValidator.requireNonBlank(batch,     "Batch");
        InputValidator.validateEmail(email);
        InputValidator.validatePhone(phone);

        checkEmailUnique(email.trim());

        String id = IdGenerator.getNextStudentId();

        Student student = new Student(id, validFirst, validLast, email.trim(), phone.trim(), validBatch);
        students.add(student);
        return student;
    }

    public List<Student> listStudents() {
        return new ArrayList<>(students);
    }

    public Student searchById(String id) {
        InputValidator.requireNonBlank(id, "Student ID");
        return students.stream()
                .filter(s -> s.getId().equals(id.trim()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Student", id.trim()));
    }

    public Student deactivateStudent(String id) {
        Student student = searchById(id);
        if (!student.isActive()) {
            throw new InvalidInputException("Student '" + id.trim() + "' is already inactive.");
        }
        student.setActive(false);
        return student;
    }

    public Student updateStudent(String id, String newFirstName, String newLastName,
                                 String newEmail, String newPhone, String newBatch) {
        Student student = searchById(id);

        if (newFirstName != null && !newFirstName.isBlank()) {
            student.setFirstName(newFirstName.trim());
        }
        if (newLastName != null && !newLastName.isBlank()) {
            student.setLastName(newLastName.trim());
        }
        if (newEmail != null && !newEmail.isBlank()) {
            InputValidator.validateEmail(newEmail);
            if (!newEmail.trim().equalsIgnoreCase(student.getEmail())) {
                checkEmailUnique(newEmail.trim());
            }
            student.setEmail(newEmail.trim());
        }
        if (newPhone != null && !newPhone.isBlank()) {
            InputValidator.validatePhone(newPhone);
            student.setPhone(newPhone.trim());
        }
        if (newBatch != null && !newBatch.isBlank()) {
            student.setBatch(newBatch.trim());
        }
        return student;
    }

    private void checkEmailUnique(String email) {
        students.stream()
                .filter(s -> s.getEmail().equalsIgnoreCase(email))
                .findFirst()
                .ifPresent(s -> {
                    throw new InvalidInputException("Email '" + email + "' is already registered.");
                });
    }
}
