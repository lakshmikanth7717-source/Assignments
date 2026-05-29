package com.book.manager;

import com.book.model.Book;
import com.book.service.Manager;
import com.book.service.SearchStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class InventoryManager implements Manager<Book> {

    private static final Logger logger = Logger.getLogger(InventoryManager.class.getName());

    private final List<Book> inventory = new ArrayList<>();

    private static volatile InventoryManager inventoryManager ;

    private  InventoryManager()
    {

    }
    public static InventoryManager getInstance()
    {
        if(inventoryManager == null)
        {
            synchronized (InventoryManager.class)
            {
                //Double check for thread safety;
                if(inventoryManager == null)
                {
                    inventoryManager = new InventoryManager();
                }
            }
        }
        return inventoryManager;
    }
    @Override
    public void add(Book book) {
        if (book == null || book.getIsbn() == null) {
            logger.severe("Add failed — book or ISBN is null");
            throw new IllegalArgumentException("Book and ISBN must not be null");
        }
        if (findById(book.getIsbn()).isPresent()) {
            logger.warning("Add failed — duplicate ISBN: " + book.getIsbn());
            throw new IllegalStateException("Book with ISBN " + book.getIsbn() + " already exists");
        }
        inventory.add(book);
        logger.info("Book added: [" + book.getIsbn() + "] " + book.getTitle());
    }

    @Override
    public boolean remove(String isbn) {
        boolean removed = inventory.removeIf(b -> isbn.equals(b.getIsbn()));
        if (removed) {
            logger.info("Book removed: ISBN " + isbn);
        } else {
            logger.warning("Remove failed — ISBN not found: " + isbn);
        }
        return removed;
    }

    @Override
    public boolean update(String isbn, Book updated) {
        for (int i = 0; i < inventory.size(); i++) {
            if (isbn.equals(inventory.get(i).getIsbn())) {
                inventory.set(i, updated);
                logger.info("Book updated: ISBN " + isbn);
                return true;
            }
        }
        logger.warning("Update failed — ISBN not found: " + isbn);
        return false;
    }

    @Override
    public Optional<Book> findById(String isbn) {
        return inventory.stream()
                .filter(b -> isbn.equals(b.getIsbn()))
                .findFirst();
    }

    @Override
    public List<Book> getAll() {
        return new ArrayList<>(inventory);
    }

    public List<Book> search(SearchStrategy strategy) {
        return strategy.search(inventory);
    }
}