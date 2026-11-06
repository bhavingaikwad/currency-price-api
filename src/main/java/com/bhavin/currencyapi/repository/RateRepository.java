package com.bhavin.currencyapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bhavin.currencyapi.domain.Rate;

public interface RateRepository extends JpaRepository<Rate, Long> {
}
