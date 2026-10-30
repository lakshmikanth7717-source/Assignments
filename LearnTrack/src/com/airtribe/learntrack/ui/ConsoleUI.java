package com.airtribe.learntrack.ui;

import com.airtribe.learntrack.entity.Course;
import com.airtribe.learntrack.entity.Enrollment;
import com.airtribe.learntrack.entity.Student;
import com.airtribe.learntrack.exception.LearnTrackException;
import com.airtribe.learntrack.service.CourseService;
import com.airtribe.learntrack.service.EnrollmentService;
import com.airtribe.learntrack.service.StudentService;

import java.util.List;
import java.util.Scanner;

public class ConsoleUI {

    private static final String DIVIDER = "═".repeat(62);
    private static final String THIN    = "─".repeat(62);

    private final StudentService    studentService;
    private final CourseService     courseService;
    private final EnrollmentService enrollmentService;
    private final Scanner           scanner;

    public ConsoleUI(StudentService studentService,
                     CourseService courseService,
                     EnrollmentService enrollmentService) {
        this.studentService    = studentService;
        this.courseService     = courseService;
        this.enrollmentService = enrollmentService;
        this.scanner           = new Scanner(System.in);
    }

    public void start() {
        banner();
        boolean running = true;
        while (running) {
            mainMenu();
            switch (readInt("Choice")) {
                case 1  -> manageStudents();
                case 2  -> manageCourses();
                case 3  -> manageEnrollments();
                case 4  -> dashboard();
                case 0  -> { running = false; out("\nGoodbye!\n"); }
                default -> err("Invalid option.");
            }
        }
        scanner.close();
    }

    // ─── Main Menu ─────────────────────────────────────────────

    private void mainMenu() {
        out("\n" + DIVIDER);
        out("  LearnTrack  |  Main Menu");
        out(DIVIDER);
        out("  1. Manage Students");
        out("  2. Manage Courses");
        out("  3. Manage Enrollments");
        out("  4. Dashboard");
        out("  0. Exit");
        out(DIVIDER);
    }

    // ─── Student Management ────────────────────────────────────

    private void manageStudents() {
        boolean back = false;
        while (!back) {
            out("\n" + THIN);
            out("  Student Management");
            out(THIN);
            out("  1. Add Student");
            out("  2. View All Students");
            out("  3. Find by ID");
            out("  4. Update Student");
            out("  5. Remove Student");
            out("  0. Back");
            out(THIN);
            switch (readInt("Choice")) {
                case 1  -> addStudent();
                case 2  -> viewAllStudents();
                case 3  -> findStudent();
                case 4  -> updateStudent();
                case 5  -> removeStudent();
                case 0  -> back = true;
                default -> err("Invalid option.");
            }
        }
    }

    private void addStudent() {
        out("\n--- Add Student ---");
        try {
            String firstName = prompt("First Name");
            String lastName  = prompt("Last Name");
            String email     = prompt("Email");
            String phone     = prompt("Phone (10 digits)");
            String batch     = prompt("Batch (e.g. 2024-A)");
            Student s = studentService.addStudent(firstName, lastName, email, phone, batch);
            ok("Added: " + s);
        } catch (LearnTrackException e) {
            err(e.getMessage());
        }
    }

    private void viewAllStudents() {
        List<Student> list = studentService.listStudents();
        out("\n--- All Students (" + list.size() + ") ---");
        if (list.isEmpty()) out("  (none)");
        else list.forEach(s -> out("  " + s));
    }

    private void findStudent() {
        out("\n--- Find Student ---");
        String id = prompt("Student ID");
        studentService.listStudents().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .ifPresentOrElse(
                        s  -> out("  " + s),
                        () -> err("Student with ID '" + id + "' not found.")
                );
    }

    private void updateStudent() {
        out("\n--- Update Student (blank = keep current) ---");
        try {
            String id        = prompt("Student ID");
            String firstName = promptOptional("New First Name");
            String lastName  = promptOptional("New Last Name");
            String email     = promptOptional("New Email");
            String phone     = promptOptional("New Phone");
            String batch     = promptOptional("New Batch");
            Student s = studentService.updateStudent(id, firstName, lastName, email, phone, batch);
            ok("Updated: " + s);
        } catch (LearnTrackException e) {
            err(e.getMessage());
        }
    }

    private void removeStudent() {
        out("\n--- Deactivate Student ---");
        try {
            String id = prompt("Student ID");
            if (confirm("Confirm deactivate student '" + id + "'?")) {
                studentService.deactivateStudent(id);
                ok("Student deactivated.");
            } else {
                out("  Cancelled.");
            }
        } catch (LearnTrackException e) {
            err(e.getMessage());
        }
    }

    // ─── Course Management ─────────────────────────────────────

    private void manageCourses() {
        boolean back = false;
        while (!back) {
            out("\n" + THIN);
            out("  Course Management");
            out(THIN);
            out("  1. Add Course");
            out("  2. View All Courses");
            out("  3. Find by ID");
            out("  4. Update Course");
            out("  5. Remove Course");
            out("  0. Back");
            out(THIN);
            switch (readInt("Choice")) {
                case 1  -> addCourse();
                case 2  -> viewAllCourses();
                case 3  -> findCourse();
                case 4  -> updateCourse();
                case 5  -> removeCourse();
                case 0  -> back = true;
                default -> err("Invalid option.");
            }
        }
    }

    private void addCourse() {
        out("\n--- Add Course ---");
        try {
            String courseName = prompt("Course Name");
            String desc       = prompt("Description");
            int    duration   = readInt("Duration (weeks)");
            Course c = courseService.addCourse(courseName, desc, duration);
            ok("Added: " + c);
        } catch (LearnTrackException e) {
            err(e.getMessage());
        }
    }

    private void viewAllCourses() {
        List<Course> list = courseService.listCourses();
        out("\n--- All Courses (" + list.size() + ") ---");
        if (list.isEmpty()) out("  (none)");
        else list.forEach(c -> out("  " + c));
    }

    private void findCourse() {
        out("\n--- Find Course ---");
        try {
            Course c = courseService.findById(prompt("Course ID"));
            out("  " + c);
        } catch (LearnTrackException e) {
            err(e.getMessage());
        }
    }

    private void updateCourse() {
        out("\n--- Update Course (blank = keep current) ---");
        try {
            String  id         = prompt("Course ID");
            String  name       = promptOptional("New Course Name");
            String  desc       = promptOptional("New Description");
            String  durStr     = promptOptional("New Duration in Weeks");
            String  activeStr  = promptOptional("Set Active? (true/false)");
            int     duration   = durStr.isEmpty() ? 0 : Integer.parseInt(durStr);
            Boolean active     = activeStr.isEmpty() ? null : Boolean.parseBoolean(activeStr);
            Course c = courseService.updateCourse(id, name, desc, duration, active);
            ok("Updated: " + c);
        } catch (LearnTrackException | NumberFormatException e) {
            err(e.getMessage());
        }
    }

    private void removeCourse() {
        out("\n--- Remove Course ---");
        try {
            String id = prompt("Course ID");
            out("  " + courseService.findById(id));
            if (confirm("Confirm removal?")) {
                courseService.deactivateCourse(id);
                ok("Course removed.");
            } else {
                out("  Cancelled.");
            }
        } catch (LearnTrackException e) {
            err(e.getMessage());
        }
    }

    // ─── Enrollment Management ─────────────────────────────────

    private void manageEnrollments() {
        boolean back = false;
        while (!back) {
            out("\n" + THIN);
            out("  Enrollment Management");
            out(THIN);
            out("  1. Enroll Student in Course");
            out("  2. Cancel Enrollment");
            out("  3. Mark as Completed");
            out("  4. View by Student");
            out("  5. View by Course");
            out("  6. View All Enrollments");
            out("  0. Back");
            out(THIN);
            switch (readInt("Choice")) {
                case 1  -> enrollStudent();
                case 2  -> cancelEnrollment();
                case 3  -> completeEnrollment();
                case 4  -> viewByStudent();
                case 5  -> viewByCourse();
                case 6  -> viewAllEnrollments();
                case 0  -> back = true;
                default -> err("Invalid option.");
            }
        }
    }

    private void enrollStudent() {
        out("\n--- Enroll Student ---");
        try {
            String studentId = prompt("Student ID");
            String courseId  = prompt("Course ID");
            Enrollment e = enrollmentService.enroll(studentId, courseId);
            ok("Enrolled: " + e);
        } catch (LearnTrackException e) {
            err(e.getMessage());
        }
    }

    private void cancelEnrollment() {
        out("\n--- Cancel Enrollment ---");
        try {
            Enrollment e = enrollmentService.markCancelled(prompt("Enrollment ID"));
            ok("Cancelled: " + e);
        } catch (LearnTrackException e) {
            err(e.getMessage());
        }
    }

    private void completeEnrollment() {
        out("\n--- Complete Enrollment ---");
        try {
            Enrollment e = enrollmentService.markCompleted(prompt("Enrollment ID"));
            ok("Completed: " + e);
        } catch (LearnTrackException e) {
            err(e.getMessage());
        }
    }

    private void viewByStudent() {
        out("\n--- Enrollments by Student ---");
        try {
            String studentId = prompt("Student ID");
            List<Enrollment> list = enrollmentService.getEnrollmentsByStudent(studentId);
            out("  Found " + list.size() + " enrollment(s):");
            if (list.isEmpty()) out("  (none)");
            else list.forEach(e -> out("  " + e));
        } catch (LearnTrackException e) {
            err(e.getMessage());
        }
    }

    private void viewByCourse() {
        out("\n--- Enrollments by Course ---");
        try {
            String courseId = prompt("Course ID");
            List<Enrollment> list = enrollmentService.listEnrollments().stream()
                    .filter(e -> e.getCourseId().equals(courseId.trim()))
                    .collect(java.util.stream.Collectors.toList());
            out("  Found " + list.size() + " enrollment(s):");
            if (list.isEmpty()) out("  (none)");
            else list.forEach(e -> out("  " + e));
        } catch (LearnTrackException e) {
            err(e.getMessage());
        }
    }

    private void viewAllEnrollments() {
        List<Enrollment> list = enrollmentService.listEnrollments();
        out("\n--- All Enrollments (" + list.size() + ") ---");
        if (list.isEmpty()) out("  (none)");
        else list.forEach(e -> out("  " + e));
    }

    // ─── Dashboard ─────────────────────────────────────────────

    private void dashboard() {
        long activeEnrollments = enrollmentService.listEnrollments().stream()
                .filter(Enrollment::isActive).count();
        out("\n" + DIVIDER);
        out("  Dashboard");
        out(DIVIDER);
        out(String.format("  Students    : %d", studentService.listStudents().size()));
        out(String.format("  Courses     : %d", courseService.listCourses().size()));
        out(String.format("  Enrollments : %d", enrollmentService.listEnrollments().size()));
        out(String.format("  Active Enrollments : %d", activeEnrollments));
        out(DIVIDER);
    }

    // ─── I/O Helpers ───────────────────────────────────────────

    private void banner() {
        out(DIVIDER);
        out("         LearnTrack — Student & Course Manager");
        out("              Console Edition  |  v1.0");
        out(DIVIDER);
    }

    private String prompt(String label) {
        System.out.print("  " + label + ": ");
        return scanner.nextLine().trim();
    }

    private String promptOptional(String label) {
        System.out.print("  " + label + " (optional): ");
        return scanner.nextLine().trim();
    }

    private int readInt(String label) {
        while (true) {
            System.out.print("  " + label + ": ");
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                err("Please enter a valid integer.");
            }
        }
    }

    private boolean confirm(String question) {
        System.out.print("  " + question + " (yes/no): ");
        return "yes".equalsIgnoreCase(scanner.nextLine().trim());
    }

    private void out(String msg) { System.out.println(msg); }
    private void ok(String msg)  { System.out.println("  [OK]    " + msg); }
    private void err(String msg) { System.out.println("  [ERROR] " + msg); }
}