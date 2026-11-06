package com.bhavin.currencyapi.client;

import java.util.Collection;
import java.util.List;

import com.bhavin.currencyapi.domain.Rate;

/**
 * A source of exchange rates. The rest of the application depends only on this
 * interface, so a provider can be swapped or mocked without touching service code.
 */
public interface RateProvider {

    /** Short name stored with every rate, e.g. "ECB". */
    String name();

    /**
     * Fetches the latest published rates of {@code base} against each of {@code quotes}.
     * Returned rates are not yet saved.
     *
     * @throws RateProviderException if the provider cannot supply the rates
     * @throws IllegalArgumentException if base is blank or quotes is empty
     */
    List<Rate> fetchLatest(String base, Collection<String> quotes);
}
