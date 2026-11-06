package com.accountbook.merchant.internal;

import com.accountbook.common.web.PageResult;
import com.accountbook.merchant.api.MerchantDraft;
import com.accountbook.merchant.api.MerchantView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchants")
class MerchantController {

    private final MerchantService service;

    MerchantController(MerchantService service) {
        this.service = service;
    }

    @GetMapping
    PageResult<MerchantView> list(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) String country,
                                  @RequestParam(required = false) String city,
                                  @RequestParam(required = false) String district,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return service.search(q, country, city, district, type, page, size);
    }

    @GetMapping("/{id}")
    MerchantView get(@PathVariable long id) {
        return service.require(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MerchantView create(@Valid @RequestBody MerchantDraft body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    MerchantView update(@PathVariable long id, @Valid @RequestBody MerchantDraft body) {
        return service.update(id, body);
    }
}
