package org.crumb.be.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.crumb.be.dto.GoogleBakeryDto;
import org.crumb.be.dto.KakaoBakeryDto;
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
    public List<KakaoBakeryDto> getBakeries(
            @RequestParam @NotNull Double lat,
            @RequestParam @NotNull Double lng,
            @RequestParam(defaultValue = "1500") @Min(100) @Max(50000) Integer radius,
            @RequestParam Integer size
    ) {
        return bakerySearchService.search(lat, lng, radius, size);
    }
}
