package com.bhavin.currencyapi.controller;

import com.bhavin.currencyapi.domain.Rate;
import java.math.BigDecimal;
import java.time.Instant;

public record RateDto(String source, String target, BigDecimal value, Instant rateTime, String provider) {

    static RateDto from(Rate rate) {
        return new RateDto(rate.getSource(), rate.getTarget(), rate.getValue(), rate.getRateTime(), rate.getProvider());
    }
}
