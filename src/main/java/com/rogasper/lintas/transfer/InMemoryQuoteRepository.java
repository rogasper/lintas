package com.rogasper.lintas.transfer;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryQuoteRepository implements QuoteRepository {

    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();

    @Override
    public Quote save(Quote quote) {
        quotes.put(quote.id(), quote);
        return quote;
    }

    @Override
    public Optional<Quote> findById(String id) {
        return Optional.ofNullable(quotes.get(id));
    }

    @Override
    public List<Quote> findAll() {
        return List.copyOf(quotes.values());
    }
}
