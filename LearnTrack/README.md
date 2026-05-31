# LearnTrack — Student & Course Management System

A console-based management system for students, courses, and enrollments. Built in Java using core OOP principles — encapsulation, inheritance, and separation of concerns between service logic and UI.

---

## Features

**Student Management**
- Add a new student (first name, last name, email, phone, batch)
- View all students
- Search student by ID
- Deactivate a student (soft delete — sets `active = false`, data is retained)

**Course Management**
- Add a new course (name, description, duration in weeks)
- View all courses
- Activate / deactivate a course

**Enrollment Management**
- Enroll a student in a course
- View all enrollments for a student
- Mark an enrollment as Completed or Cancelled

---

## Project Structure

```
LearnTrack/
├── src/
│   └── com/airtribe/learntrack/
│       ├── Main.java                  ← entry point, all menus
│       ├── entity/
│       │   ├── BaseEntity.java        ← abstract base (id, createdAt)
│       │   ├── Person.java            ← abstract (firstName, lastName, email, phone)
│       │   ├── Student.java           ← extends Person (batch, active)
│       │   ├── Course.java            ← extends BaseEntity
│       │   └── Enrollment.java        ← extends BaseEntity (Status enum)
│       ├── service/
│       │   ├── StudentService.java
│       │   ├── CourseService.java
│       │   └── EnrollmentService.java
│       ├── exception/
│       │   ├── LearnTrackException.java
│       │   ├── EntityNotFoundException.java
│       │   ├── InvalidInputException.java
│       │   ├── DuplicateEnrollmentException.java
│       │   └── CourseCapacityException.java
│       └── util/
│           ├── IdGenerator.java
│           └── InputValidator.java
├── docs/
│   └── JVM_basics.md
├── README.md
├── setup_instructions.md
└── design_notes.md
```

---

## How to Compile and Run

**Requirements:** JDK 17 or above

```bash
# From the project root (LearnTrack/)

# Step 1 — Compile all source files into the out/ directory
find src/com -name "*.java" | xargs javac -d out

# Step 2 — Run the application
java -cp out com.airtribe.learntrack.Main
```

**On Windows (Command Prompt):**
```cmd
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
java -cp out com.airtribe.learntrack.Main
```

---

## Sample Session

```
========================================
   Welcome to LearnTrack
========================================

--- Main Menu ---
1. Student Management
2. Course Management
3. Enrollment Management
0. Exit
  Choose: 1

--- Student Management ---
1. Add Student
2. View All Students
3. Search Student by ID
4. Deactivate Student
0. Back
  Choose: 1

-- Add Student --
  First Name: Alice
  Last Name: Smith
  Email: alice@example.com
  Phone (10 digits): 9876543210
  Batch (e.g. 2024-A): 2024-A
[OK] Student added: Student[id=STU-1001 | name=Alice Smith | ...]
```

---

## Technology

| Item | Detail |
|---|---|
| Language | Java 17 |
| Runtime | OpenJDK 17.0.2 (Amazon Corretto) |
| Storage | In-memory (`ArrayList`) |
| Build | `javac` (no build tool required) |
| Dependencies | None (pure Java SE) |
