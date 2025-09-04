package org.crumb.be.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.crumb.be.dto.BakeryDto;
import org.crumb.be.service.BakerySearchService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/bakery")
public class BakeryController {

    private final BakerySearchService bakerySearchService;

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BakeryDto> getBakeries(
            @RequestParam @NotNull Double lat,
            @RequestParam @NotNull Double lng,
            @RequestParam(defaultValue = "1500") @Min(100) @Max(50000) Integer radius,
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit,
            @RequestParam(required = false, defaultValue = "distance") String sort // distance|rating
    ) {
        return bakerySearchService.search(lat, lng, radius, limit, sort);
    }
}
