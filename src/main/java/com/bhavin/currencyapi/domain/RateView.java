package com.bhavin.currencyapi.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record RateView(String source, String target, BigDecimal value, Instant rateTime, String provider) {

    public static RateView from(Rate rate) {
        return new RateView(rate.getSource(), rate.getTarget(), rate.getValue(), rate.getRateTime(), rate.getProvider());
    }
}
