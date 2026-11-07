package com.bhavin.currencyapi.controller;

import com.bhavin.currencyapi.domain.ProviderName;
import com.bhavin.currencyapi.domain.RateView;
import com.bhavin.currencyapi.service.LatestRatesCache;
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
    private final LatestRatesCache latestRatesCache;

    public RateController(RateQueryService rateQueryService, LatestRatesCache latestRatesCache) {
        this.rateQueryService = rateQueryService;
        this.latestRatesCache = latestRatesCache;
    }

    @GetMapping("/latest-naive")
    public List<RateView> latestNaive(@RequestParam ProviderName provider) {
        return rateQueryService.latestPerPairNaive(provider.name());
    }

    @GetMapping("/latest")
    public List<RateView> latest(@RequestParam ProviderName provider) {
        return rateQueryService.latestPerPair(provider.name());
    }

    @GetMapping("/latest-cached")
    public List<RateView> latestCached(@RequestParam ProviderName provider) {
        return latestRatesCache.latest(provider.name());
    }
}
