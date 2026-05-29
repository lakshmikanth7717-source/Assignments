package com.book.manager;

import com.book.model.Patron;
import com.book.service.Manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PatronManager implements Manager<Patron> {

    private final List<Patron> patrons = new ArrayList<>();

    private static volatile PatronManager instance;

    private PatronManager() {}

    public static PatronManager getInstance() {
        if (instance == null) {
            synchronized (PatronManager.class) {
                if (instance == null) {
                    instance = new PatronManager();
                }
            }
        }
        return instance;
    }

    @Override
    public void add(Patron patron) {
        if (patron == null || patron.getPatronId() == null) {
            throw new IllegalArgumentException("Patron and patronId cannot be null");
        }
        if (findById(patron.getPatronId()).isPresent()) {
            throw new IllegalStateException("Patron with ID " + patron.getPatronId() + " already exists");
        }
        patrons.add(patron);
        System.out.println("New member added: " + patron.getPatronId());
    }

    @Override
    public boolean remove(String patronId) {
        boolean removed = patrons.removeIf(p -> patronId.equals(p.getPatronId()));
        if (removed) {
            System.out.println("Removed patron with ID: " + patronId);
        } else {
            System.out.println("No patron found with ID: " + patronId);
        }
        return removed;
    }

    @Override
    public boolean update(String patronId, Patron updated) {
        if (patronId == null || updated == null) {
            throw new IllegalArgumentException("PatronId and updated patron cannot be null");
        }
        for (int i = 0; i < patrons.size(); i++) {
            if (patronId.equals(patrons.get(i).getPatronId())) {
                patrons.set(i, updated);
                System.out.println("Updated patron with ID: " + patronId);
                return true;
            }
        }
        System.out.println("No patron found with ID: " + patronId);
        return false;
    }

    @Override
    public Optional<Patron> findById(String patronId) {
        return patrons.stream()
                .filter(p -> patronId.equals(p.getPatronId()))
                .findFirst();
    }

    @Override
    public List<Patron> getAll() {
        return new ArrayList<>(patrons);
    }
}