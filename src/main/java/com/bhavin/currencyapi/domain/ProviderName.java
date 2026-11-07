package com.bhavin.currencyapi.domain;

/** The data providers the API accepts. Anything else is rejected with HTTP 400. */
public enum ProviderName {
    ECB,
    BINANCE
}
