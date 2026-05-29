package com.book.service;

import com.book.model.Book;

import java.util.List;

public class IsbnSearchStrategy implements SearchStrategy {

    private final String isbn;

    public IsbnSearchStrategy(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            throw new IllegalArgumentException("ISBN must not be blank");
        }
        this.isbn = isbn;
    }

    @Override
    public List<Book> search(List<Book> books) {
        return books.stream()
                .filter(b -> isbn.equals(b.getIsbn()))
                .toList();
    }
}