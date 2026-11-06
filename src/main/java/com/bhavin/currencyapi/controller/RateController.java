package com.bhavin.currencyapi.controller;

import com.bhavin.currencyapi.service.RateQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rates")
public class RateController {

    private final RateQueryService rateQueryService;

    public RateController(RateQueryService rateQueryService) {
        this.rateQueryService = rateQueryService;
    }

    /** Naive N+1 version (M4). */
    @GetMapping("/latest-naive")
    public List<RateDto> latestNaive(@RequestParam String provider) {
        return rateQueryService.latestPerPairNaive(provider).stream().map(RateDto::from).toList();
    }
}
