package com.book.model;

public class Book {

    private final String isbn;
    private final String title;
    private final String author;
    private final int publicationYear;
    private boolean available;

    private Book(Builder builder) {
        this.isbn = builder.isbn;
        this.title = builder.title;
        this.author = builder.author;
        this.publicationYear = builder.publicationYear;
        this.available = true;
    }

    public static class Builder {

        private final String isbn;
        private String title;
        private String author;
        private int publicationYear;

        public Builder(String isbn) {
            if (isbn == null || isbn.isBlank()) {
                throw new IllegalArgumentException("ISBN must not be blank");
            }
            this.isbn = isbn;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder publicationYear(int publicationYear) {
            this.publicationYear = publicationYear;
            return this;
        }

        public Book build() {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Title must not be blank");
            }
            if (author == null || author.isBlank()) {
                throw new IllegalArgumentException("Author must not be blank");
            }
            return new Book(this);
        }
    }

    public String getIsbn() { return isbn; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public int getPublicationYear() { return publicationYear; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    @Override
    public String toString() {
        return "Book{" +
                "isbn='" + isbn + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", publicationYear=" + publicationYear +
                ", available=" + available +
                '}';
    }
}