package com.book.service;

import java.util.List;
import java.util.Optional;

public interface Manager<T> {
    void add(T entity);
    boolean remove(String id);
    boolean update(String id, T entity);
    Optional<T> findById(String id);
    List<T> getAll();
}