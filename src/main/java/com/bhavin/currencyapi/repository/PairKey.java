package com.bhavin.currencyapi.repository;

/** A (source, target) currency pair, e.g. BTC/USDT. */
public record PairKey(String source, String target) {
}
