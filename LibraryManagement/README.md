zip # Library Management System

A plain Java library management system demonstrating core OOP principles and design patterns.

---

## Package Structure

```
src/com/book/
  model/       Book, Patron, BorrowRecord
  manager/     InventoryManager, PatronManager, BorrowingManager
  service/     Manager<T>, SearchStrategy, TitleSearchStrategy,
               AuthorSearchStrategy, IsbnSearchStrategy
  record/      BorrowRecord
```

---

## Class Diagram

```
«interface»                        «interface»
Manager<T>                         SearchStrategy
+ add(T)                           + search(List<Book>) : List<Book>
+ remove(String) : boolean              ▲
+ update(String, T) : boolean           │
+ findById(String) : Optional<T>   ┌────┴───────────────────┐
+ getAll() : List<T>               │           │             │
     ▲               ▲        TitleSearch  AuthorSearch  IsbnSearch
     │               │         Strategy    Strategy      Strategy
     │               │
InventoryManager  PatronManager
(Singleton)       (Singleton)
+ getInstance()   + getInstance()
+ search(SearchStrategy)
     ▲                  ▲
     │ uses             │ uses
     └────────┬─────────┘
              │
       BorrowingManager (Singleton)
       + getInstance()
       + checkoutBook(patronId, isbn)
       + returnBook(patronId, isbn)
       + getAvailableBooks()
       + getBorrowedBooks()
       + getActiveLoans(patronId)
              │ owns
              ▼
         BorrowRecord
         - patronId
         - isbn
         - checkoutDate
         - returnDate
         - status: ACTIVE | RETURNED


  Book                          Patron
  + Builder (inner class)       - patronId
  - isbn (final)                - name
  - title (final)               - email
  - author (final)              - phone
  - publicationYear (final)     - active
  - available
```

---

## Design Patterns

| Pattern | Where |
|---|---|
| **Singleton** | `InventoryManager`, `PatronManager`, `BorrowingManager` |
| **Builder** | `Book.Builder` |
| **Strategy** | `SearchStrategy` + `TitleSearchStrategy`, `AuthorSearchStrategy`, `IsbnSearchStrategy` |
| **Repository** | `InventoryManager`, `PatronManager` via `Manager<T>` |
| **Dependency Injection** | `BorrowingManager` pulls Singleton instances of both managers |

---

## Usage

```java
// get singleton instances
InventoryManager inventory = InventoryManager.getInstance();
PatronManager patrons       = PatronManager.getInstance();
BorrowingManager borrowing  = BorrowingManager.getInstance();

// add a book using Builder
Book book = new Book.Builder("978-0-13-235088-4")
        .title("Clean Code")
        .author("Robert Martin")
        .publicationYear(2008)
        .build();
inventory.add(book);

// add a patron
patrons.add(new Patron("P001", "Alice", "alice@example.com", "555-1234"));

// borrow and return
borrowing.checkoutBook("P001", "978-0-13-235088-4");
borrowing.returnBook("P001", "978-0-13-235088-4");

// search
inventory.search(new TitleSearchStrategy("clean"));
inventory.search(new AuthorSearchStrategy("martin"));
inventory.search(new IsbnSearchStrategy("978-0-13-235088-4"));
```