package com.book.service;

import com.book.model.Book;

import java.util.List;

public class TitleSearchStrategy implements SearchStrategy {

    private final String query;

    public TitleSearchStrategy(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }
        this.query = query.toLowerCase();
    }

    @Override
    public List<Book> search(List<Book> books) {
        return books.stream()
                .filter(b -> b.getTitle() != null && b.getTitle().toLowerCase().contains(query))
                .toList();
    }
}