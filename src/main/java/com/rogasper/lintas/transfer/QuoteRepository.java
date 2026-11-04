package com.rogasper.lintas.transfer;

import java.util.List;
import java.util.Optional;

public interface QuoteRepository {

    Quote save(Quote quote);

    Optional<Quote> findById(String id);

    List<Quote> findAll();
}
