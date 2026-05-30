import com.book.manager.BorrowingManager;
import com.book.manager.InventoryManager;
import com.book.manager.PatronManager;
import com.book.model.Book;
import com.book.model.Patron;
import com.book.service.AuthorSearchStrategy;
import com.book.service.IsbnSearchStrategy;
import com.book.service.TitleSearchStrategy;

import java.util.List;

public class LibraryManagement {

    public static void main(String[] args) {
        InventoryManager inventory = InventoryManager.getInstance();
        PatronManager patronMgr = PatronManager.getInstance();
        BorrowingManager borrowing = BorrowingManager.getInstance();

        // ── 1. Add books ──────────────────────────────────────────────────────
        System.out.println("=== Add Books ===");

        Book cleanCode = new Book.Builder("978-0132350884")
                .title("Clean Code")
                .author("Robert C. Martin")
                .publicationYear(2008)
                .build();

        Book effectiveJava = new Book.Builder("978-0134685991")
                .title("Effective Java")
                .author("Joshua Bloch")
                .publicationYear(2018)
                .build();

        Book designPatterns = new Book.Builder("978-0201633610")
                .title("Design Patterns")
                .author("Gang of Four")
                .publicationYear(1994)
                .build();

        inventory.add(cleanCode);
        inventory.add(effectiveJava);
        inventory.add(designPatterns);

        // ── 2. Add patrons ────────────────────────────────────────────────────
        System.out.println("\n=== Add Patrons ===");

        // new Patron(patronId, name, email, phone)
        Patron alice = new Patron("P001", "Alice Johnson", "alice@example.com", "555-1001");
        Patron bob   = new Patron("P002", "Bob Smith",     "bob@example.com",   "555-1002");

        patronMgr.add(alice);
        patronMgr.add(bob);

        // ── 3. Search books ───────────────────────────────────────────────────
        System.out.println("\n=== Search by Title: 'java' ===");
        List<Book> byTitle = inventory.search(new TitleSearchStrategy("java"));
        byTitle.forEach(System.out::println);

        System.out.println("\n=== Search by Author: 'martin' ===");
        List<Book> byAuthor = inventory.search(new AuthorSearchStrategy("martin"));
        byAuthor.forEach(System.out::println);

        System.out.println("\n=== Search by ISBN: '978-0201633610' ===");
        List<Book> byIsbn = inventory.search(new IsbnSearchStrategy("978-0201633610"));
        byIsbn.forEach(System.out::println);

        // ── 4. Checkout ───────────────────────────────────────────────────────
        System.out.println("\n=== Checkout ===");
        // checkoutBook(patronId, isbn)
        borrowing.checkoutBook("P001", "978-0132350884"); // Alice borrows Clean Code
        borrowing.checkoutBook("P002", "978-0134685991"); // Bob borrows Effective Java

        System.out.println("\nAvailable books after checkout:");
        borrowing.getAvailableBooks().forEach(b -> System.out.println("  " + b.getTitle()));

        System.out.println("Borrowed books:");
        borrowing.getBorrowedBooks().forEach(b -> System.out.println("  " + b.getTitle()));

        // ── 5. Return ─────────────────────────────────────────────────────────
        System.out.println("\n=== Return ===");
        // returnBook(patronId, isbn)
        borrowing.returnBook("P001", "978-0132350884"); // Alice returns Clean Code

        System.out.println("\nAvailable books after return:");
        borrowing.getAvailableBooks().forEach(b -> System.out.println("  " + b.getTitle()));

        // ── 6. Update a book ──────────────────────────────────────────────────
        System.out.println("\n=== Update Book ===");
        Book updatedDesignPatterns = new Book.Builder("978-0201633610")
                .title("Design Patterns: Elements of Reusable Object-Oriented Software")
                .author("Gang of Four")
                .publicationYear(1994)
                .build();
        // update(isbn, updatedBook)
        inventory.update("978-0201633610", updatedDesignPatterns);
        inventory.findById("978-0201633610").ifPresent(System.out::println);

        // ── 7. Update a patron ────────────────────────────────────────────────
        System.out.println("\n=== Update Patron ===");
        Patron updatedAlice = new Patron("P001", "Alice Johnson-Brown", "alice.brown@example.com", "555-9999");
        // update(patronId, updatedPatron)
        patronMgr.update("P001", updatedAlice);
        patronMgr.findById("P001").ifPresent(System.out::println);

        // ── 8. Remove ─────────────────────────────────────────────────────────
        System.out.println("\n=== Remove ===");
        // remove(isbn)
        boolean bookRemoved = inventory.remove("978-0201633610");
        System.out.println("Design Patterns removed: " + bookRemoved);

        // remove(patronId)
        boolean patronRemoved = patronMgr.remove("P002");
        System.out.println("Bob removed: " + patronRemoved);

        // ── 9. Final state ────────────────────────────────────────────────────
        System.out.println("\n=== All Remaining Books ===");
        inventory.getAll().forEach(System.out::println);

        System.out.println("\n=== All Remaining Patrons ===");
        patronMgr.getAll().forEach(System.out::println);
    }
}