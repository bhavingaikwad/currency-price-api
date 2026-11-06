package com.bhavin.currencyapi.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One observed exchange rate or price: how much of {@code target} one unit of
 * {@code source} was worth at {@code rateTime}, according to {@code provider}.
 */
@Entity
@Table(name = "rates")
public class Rate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(nullable = false, length = 16)
    private String target;

    @Column(name = "rate_time", nullable = false)
    private Instant rateTime;

    @Column(nullable = false, precision = 28, scale = 12)
    private BigDecimal value;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Rate() {
        // required by JPA
    }

    public Rate(String source, String target, Instant rateTime, BigDecimal value, String provider) {
        this.source = source;
        this.target = target;
        this.rateTime = rateTime;
        this.value = value;
        this.provider = provider;
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public String getTarget() { return target; }
    public Instant getRateTime() { return rateTime; }
    public BigDecimal getValue() { return value; }
    public String getProvider() { return provider; }
    public Instant getCreatedAt() { return createdAt; }
}
