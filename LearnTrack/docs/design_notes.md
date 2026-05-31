# Design Notes

## Why ArrayList for Storage

All three services store data in `ArrayList<T>` rather than a `HashMap` or database.

**Reason:** This is an in-memory console application with no persistence requirement. `ArrayList` is the simplest sequential structure — it is easy to iterate for displaying records, easy to filter with streams for lookups, and requires no key management. A `HashMap` would offer O(1) ID lookups, but it adds complexity (key/value separation, no ordering guarantee) that isn't justified when the dataset fits entirely in memory and the application runs in a single session.

```java
// StudentService
private final List<Student> students = new ArrayList<>();

// Lookup by ID — linear scan, acceptable for in-memory use
students.stream()
        .filter(s -> s.getId().equals(id))
        .findFirst()
        .orElseThrow(...);
```

If this application were extended to persist data or serve many concurrent users, the `ArrayList` would be replaced with a database-backed repository — the service interfaces would remain unchanged, making the swap straightforward.

---

## Why Static Members — and Where

Static fields and methods belong to the **class**, not to any instance. They are used in two places in this project:

**`IdGenerator` — static counters**

```java
public final class IdGenerator {
    private static int studentIdCounter    = 1000;
    private static int courseIdCounter     = 2000;
    private static int enrollmentIdCounter = 3000;

    public static String getNextStudentId() { return "STU-" + (++studentIdCounter); }
}
```

The counters must be static because ID generation is a single shared sequence — if each caller got its own counter, two different parts of the code could generate the same ID. Making them static ensures one counter exists for the entire application lifetime. The class is `final` with a private constructor to prevent instantiation — it is a pure utility, not an object.

**`InputValidator` — static methods**

```java
public final class InputValidator {
    public static void validateEmail(String email) { ... }
    public static String requireNonBlank(String value, String field) { ... }
}
```

Validation has no state — the same rules apply every time regardless of who calls them. Static methods here are the right choice because creating an `InputValidator` object would add overhead with no benefit. These are pure functions: same input always produces the same result.

**Where static is intentionally avoided:** Service classes (`StudentService`, `CourseService`, `EnrollmentService`) use instance fields for their data stores. This keeps each service's data encapsulated within that instance and makes the services independently testable.

---

## Where Inheritance Is Used — and What It Gains

The entity hierarchy has two levels of inheritance:

```
BaseEntity  (abstract)
│   id, createdAt, equals(), hashCode(), toString()
│
├── Person  (abstract)  extends BaseEntity
│     firstName, lastName, email, phone, getFullName()
│     │
│     └── Student  extends Person
│             batch, active
│
├── Course  extends BaseEntity
│     courseName, description, durationInWeeks, active
│
└── Enrollment  extends BaseEntity
      studentId, courseId, status (enum)
```

**What `BaseEntity` gains:**
Every stored entity needs an ID and a creation timestamp. Without `BaseEntity`, those three fields plus `equals`/`hashCode` would be duplicated in every class. With it, they are written once and inherited. The abstract `toString()` forces each subclass to define its own readable representation — it cannot be forgotten.

**What `Person` gains:**
`Student` is a person — it has a name, email, and phone. If an `Instructor` or `Admin` entity were added later, they would also extend `Person` and inherit those fields for free. Separating `Person` from `BaseEntity` keeps the distinction clear: `BaseEntity` is infrastructure (identity + timestamp), `Person` is domain (human actor). `Course` and `Enrollment` extend `BaseEntity` directly because they are not people.

**The key OOP principle at work — Don't Repeat Yourself (DRY):**
Every field defined in `BaseEntity` or `Person` is written exactly once but available in all subclasses. A change to how IDs are formatted (for example) is made in one place and automatically applies everywhere. This is the core practical gain from inheritance in this design.
