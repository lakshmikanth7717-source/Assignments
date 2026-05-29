package com.book.manager;

import com.book.model.Book;
import com.book.record.BorrowRecord;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BorrowingManager {

    private final InventoryManager inventoryManager;
    private final PatronManager patronManager;
    private final List<BorrowRecord> records = new ArrayList<>();

    private static volatile BorrowingManager instance;

    private BorrowingManager() {
        this.inventoryManager = InventoryManager.getInstance();
        this.patronManager = PatronManager.getInstance();
    }

    public static BorrowingManager getInstance() {
        if (instance == null) {
            synchronized (BorrowingManager.class) {
                if (instance == null) {
                    instance = new BorrowingManager();
                }
            }
        }
        return instance;
    }

    public void checkoutBook(String patronId, String isbn)
    {
        if (inventoryManager.findById(isbn).isPresent() && patronManager.findById(patronId).isPresent())
        {
            Book book = inventoryManager.findById(isbn).get();
            if (!book.isAvailable()) {
                throw new IllegalStateException("Book with ISBN " + isbn + " is not available");
            }
            book.setAvailable(false);
            records.add(new BorrowRecord(patronId, isbn, LocalDate.now()));
            System.out.println("Checked out ISBN " + isbn + " to patron " + patronId);
        }
        else
        {
            throw new IllegalArgumentException("Patron or book not found");
        }
    }

    public void returnBook(String patronId, String isbn)
    {
        if (inventoryManager.findById(isbn).isEmpty() || patronManager.findById(patronId).isEmpty())
        {
            throw new IllegalArgumentException("Patron or book not found for the return");
        }

        Book book = inventoryManager.findById(isbn).get();
        if (book.isAvailable()) {
            throw new IllegalStateException("Book with ISBN " + isbn + " is not currently checked out");
        }

        BorrowRecord activeRecord = records.stream()
                .filter(r -> r.getPatronId().equals(patronId)
                        && r.getIsbn().equals(isbn)
                        && r.getStatus() == BorrowRecord.Status.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active borrow record found for patron " + patronId + " and ISBN " + isbn));

        book.setAvailable(true);
        activeRecord.setReturnDate(LocalDate.now());
        activeRecord.setStatus(BorrowRecord.Status.RETURNED);
        System.out.println("Returned ISBN " + isbn + " by patron " + patronId);
    }

    public List<Book> getAvailableBooks() {
        return inventoryManager.getAll().stream()
                .filter(Book::isAvailable)
                .toList();
    }

    public List<Book> getBorrowedBooks() {
        return inventoryManager.getAll().stream()
                .filter(b -> !b.isAvailable())
                .toList();
    }
}
