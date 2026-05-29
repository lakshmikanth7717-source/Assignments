package com.book.service;

import com.book.model.Book;

import java.util.List;

public interface SearchStrategy {

    List<Book> search(List<Book> books);
}
