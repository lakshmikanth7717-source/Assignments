package com.book.record;

import java.time.LocalDate;

public class BorrowRecord {

    public enum Status { ACTIVE, RETURNED }

    private String patronId;
    private String isbn;
    private LocalDate checkoutDate;
    private LocalDate returnDate;
    private Status status;

    public BorrowRecord(String patronId, String isbn, LocalDate checkoutDate) {
        this.patronId = patronId;
        this.isbn = isbn;
        this.checkoutDate = checkoutDate;
        this.status = Status.ACTIVE;
    }

    public String getPatronId() { return patronId; }
    public String getIsbn() { return isbn; }
    public LocalDate getCheckoutDate() { return checkoutDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    @Override
    public String toString() {
        return "BorrowRecord{" +
                "patronId='" + patronId + '\'' +
                ", isbn='" + isbn + '\'' +
                ", checkoutDate=" + checkoutDate +
                ", returnDate=" + returnDate +
                ", status=" + status +
                '}';
    }
}