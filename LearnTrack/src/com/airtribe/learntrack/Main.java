package com.airtribe.learntrack;

import com.airtribe.learntrack.entity.Course;
import com.airtribe.learntrack.entity.Enrollment;
import com.airtribe.learntrack.entity.Student;
import com.airtribe.learntrack.exception.LearnTrackException;
import com.airtribe.learntrack.service.CourseService;
import com.airtribe.learntrack.service.EnrollmentService;
import com.airtribe.learntrack.service.StudentService;

import java.util.List;
import java.util.Scanner;

public class Main {

    private static final Scanner         scanner           = new Scanner(System.in);
    private static final StudentService  studentService    = new StudentService();
    private static final CourseService   courseService     = new CourseService();
    private static final EnrollmentService enrollmentService =
            new EnrollmentService(studentService, courseService);

    // ─────────────────────────────────────────────
    //  Entry point
    // ─────────────────────────────────────────────
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Welcome to LearnTrack");
        System.out.println("========================================");

        boolean running = true;
        while (running) {
            System.out.println("\n--- Main Menu ---");
            System.out.println("1. Student Management");
            System.out.println("2. Course Management");
            System.out.println("3. Enrollment Management");
            System.out.println("0. Exit");

            int choice = readInt("Choose");
            switch (choice) {
                case 1  -> studentMenu();
                case 2  -> courseMenu();
                case 3  -> enrollmentMenu();
                case 0  -> { running = false; System.out.println("Goodbye!"); }
                default -> System.out.println("[ERROR] Invalid option. Please enter 0-3.");
            }
        }
        scanner.close();
    }

    // ─────────────────────────────────────────────
    //  Student Menu
    // ─────────────────────────────────────────────
    private static void studentMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Student Management ---");
            System.out.println("1. Add Student");
            System.out.println("2. View All Students");
            System.out.println("3. Search Student by ID");
            System.out.println("4. Deactivate Student");
            System.out.println("0. Back");

            int choice = readInt("Choose");
            switch (choice) {
                case 1  -> addStudent();
                case 2  -> viewAllStudents();
                case 3  -> searchStudentById();
                case 4  -> deactivateStudent();
                case 0  -> back = true;
                default -> System.out.println("[ERROR] Invalid option. Please enter 0-4.");
            }
        }
    }

    private static void addStudent() {
        System.out.println("\n-- Add Student --");
        try {
            String firstName = readLine("First Name");
            String lastName  = readLine("Last Name");
            String email     = readLine("Email");
            String phone     = readLine("Phone (10 digits)");
            String batch     = readLine("Batch (e.g. 2024-A)");

            Student s = studentService.addStudent(firstName, lastName, email, phone, batch);
            System.out.println("[OK] Student added: " + s);
        } catch (LearnTrackException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    private static void viewAllStudents() {
        List<Student> list = studentService.listStudents();
        System.out.println("\n-- All Students (" + list.size() + ") --");
        if (list.isEmpty()) {
            System.out.println("  No students found.");
        } else {
            for (Student s : list) {
                System.out.println("  " + s);
            }
        }
    }

    private static void searchStudentById() {
        System.out.println("\n-- Search Student --");
        try {
            String id = readLine("Student ID");
            Student s = studentService.searchById(id);
            System.out.println("  Found: " + s);
        } catch (LearnTrackException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    private static void deactivateStudent() {
        System.out.println("\n-- Deactivate Student --");
        try {
            String id = readLine("Student ID");
            Student s = studentService.deactivateStudent(id);
            System.out.println("[OK] Student deactivated: " + s);
        } catch (LearnTrackException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  Course Menu
    // ─────────────────────────────────────────────
    private static void courseMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Course Management ---");
            System.out.println("1. Add Course");
            System.out.println("2. View All Courses");
            System.out.println("3. Activate Course");
            System.out.println("4. Deactivate Course");
            System.out.println("0. Back");

            int choice = readInt("Choose");
            switch (choice) {
                case 1  -> addCourse();
                case 2  -> viewAllCourses();
                case 3  -> activateCourse();
                case 4  -> deactivateCourse();
                case 0  -> back = true;
                default -> System.out.println("[ERROR] Invalid option. Please enter 0-4.");
            }
        }
    }

    private static void addCourse() {
        System.out.println("\n-- Add Course --");
        try {
            String courseName = readLine("Course Name");
            String desc       = readLine("Description");
            int    duration   = readInt("Duration (weeks)");

            Course c = courseService.addCourse(courseName, desc, duration);
            System.out.println("[OK] Course added: " + c);
        } catch (LearnTrackException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    private static void viewAllCourses() {
        List<Course> list = courseService.listCourses();
        System.out.println("\n-- All Courses (" + list.size() + ") --");
        if (list.isEmpty()) {
            System.out.println("  No courses found.");
        } else {
            for (Course c : list) {
                System.out.println("  " + c);
            }
        }
    }

    private static void activateCourse() {
        System.out.println("\n-- Activate Course --");
        try {
            String id = readLine("Course ID");
            Course c = courseService.activateCourse(id);
            System.out.println("[OK] Course activated: " + c);
        } catch (LearnTrackException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    private static void deactivateCourse() {
        System.out.println("\n-- Deactivate Course --");
        try {
            String id = readLine("Course ID");
            Course c = courseService.deactivateCourse(id);
            System.out.println("[OK] Course deactivated: " + c);
        } catch (LearnTrackException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  Enrollment Menu
    // ─────────────────────────────────────────────
    private static void enrollmentMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Enrollment Management ---");
            System.out.println("1. Enroll Student in Course");
            System.out.println("2. View Enrollments for Student");
            System.out.println("3. Mark Enrollment as Completed");
            System.out.println("4. Mark Enrollment as Cancelled");
            System.out.println("0. Back");

            int choice = readInt("Choose");
            switch (choice) {
                case 1  -> enrollStudent();
                case 2  -> viewEnrollmentsByStudent();
                case 3  -> markCompleted();
                case 4  -> markCancelled();
                case 0  -> back = true;
                default -> System.out.println("[ERROR] Invalid option. Please enter 0-4.");
            }
        }
    }

    private static void enrollStudent() {
        System.out.println("\n-- Enroll Student --");
        try {
            String studentId = readLine("Student ID");
            String courseId  = readLine("Course ID");
            Enrollment e = enrollmentService.enroll(studentId, courseId);
            System.out.println("[OK] Enrolled: " + e);
        } catch (LearnTrackException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    private static void viewEnrollmentsByStudent() {
        System.out.println("\n-- Enrollments for Student --");
        try {
            String studentId = readLine("Student ID");
            List<Enrollment> list = enrollmentService.getEnrollmentsByStudent(studentId);
            System.out.println("  Found " + list.size() + " enrollment(s):");
            if (list.isEmpty()) {
                System.out.println("  None.");
            } else {
                for (Enrollment e : list) {
                    System.out.println("  " + e);
                }
            }
        } catch (LearnTrackException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    private static void markCompleted() {
        System.out.println("\n-- Mark Enrollment Completed --");
        try {
            String id = readLine("Enrollment ID");
            Enrollment e = enrollmentService.markCompleted(id);
            System.out.println("[OK] Marked completed: " + e);
        } catch (LearnTrackException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    private static void markCancelled() {
        System.out.println("\n-- Mark Enrollment Cancelled --");
        try {
            String id = readLine("Enrollment ID");
            Enrollment e = enrollmentService.markCancelled(id);
            System.out.println("[OK] Marked cancelled: " + e);
        } catch (LearnTrackException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  Input helpers
    // ─────────────────────────────────────────────
    private static String readLine(String prompt) {
        System.out.print("  " + prompt + ": ");
        return scanner.nextLine().trim();
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print("  " + prompt + ": ");
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("[ERROR] Please enter a valid number.");
            }
        }
    }
}
