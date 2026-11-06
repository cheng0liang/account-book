package com.accountbook.product.internal;

import com.accountbook.common.web.PageResult;
import com.accountbook.product.api.ProductDraft;
import com.accountbook.product.api.ProductView;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/products")
class ProductController {

    private final ProductService service;

    ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    PageResult<ProductView> list(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) String brand,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) String tag,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return service.search(q, brand, category, tag, page, size);
    }

    @GetMapping("/brands")
    List<String> brands(@RequestParam(required = false) String q, @RequestParam(defaultValue = "20") int limit) {
        return service.brands(q, limit);
    }

    @GetMapping("/tags")
    List<String> tags(@RequestParam(required = false) String q, @RequestParam(defaultValue = "20") int limit) {
        return service.tags(q, limit);
    }

    @GetMapping("/{id}")
    ProductView get(@PathVariable long id) {
        return service.require(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProductView create(@Valid @RequestBody ProductDraft body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    ProductView update(@PathVariable long id, @Valid @RequestBody ProductDraft body) {
        return service.update(id, body);
    }
}
