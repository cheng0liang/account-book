package com.accountbook.receipt.internal;

import com.accountbook.common.web.ApiException;
import com.accountbook.common.web.PageResult;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/receipts")
class ReceiptController {

    private final ReceiptService service;
    private final RecognitionService recognition;

    ReceiptController(ReceiptService service, RecognitionService recognition) {
        this.service = service;
        this.recognition = recognition;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    RecognizeResponse recognize(@RequestParam("image") MultipartFile image) {
        try {
            return recognition.recognize(image.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping
    PageResult<ReceiptSummary> list(@RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(required = false) Long merchantId,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return service.list(from, to, merchantId, q, page, size);
    }

    @GetMapping("/{id}")
    ReceiptForm get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ReceiptForm create(@Valid @RequestBody ReceiptForm body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    ReceiptForm update(@PathVariable long id, @Valid @RequestBody ReceiptForm body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/image")
    ResponseEntity<Resource> image(@PathVariable long id) {
        var path = service.imageOf(id).orElseThrow(() -> ApiException.notFound("receipt.image.notFound", id));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .contentType(MediaType.parseMediaType(ImageStore.mimeOf(path.getFileName().toString())))
                .body(new FileSystemResource(path));
    }
}
