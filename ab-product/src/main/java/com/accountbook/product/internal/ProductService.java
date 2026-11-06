package com.accountbook.product.internal;

import static com.accountbook.common.text.NameNormalizer.blankToNull;
import static com.accountbook.common.text.NameNormalizer.normalize;

import com.accountbook.common.web.ApiException;
import com.accountbook.common.web.PageResult;
import com.accountbook.product.api.ProductApi;
import com.accountbook.product.api.ProductCategory;
import com.accountbook.product.api.ProductDraft;
import com.accountbook.product.api.ProductView;
import com.accountbook.product.api.event.ProductChangedEvent;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService implements ProductApi {

    static final int MAX_TAGS = 20;

    private final ProductRepository repo;
    private final ApplicationEventPublisher events;

    ProductService(ProductRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    @Override
    public Optional<ProductView> find(long id) {
        return repo.findById(id);
    }

    @Override
    public ProductView require(long id) {
        return find(id).orElseThrow(() -> ApiException.notFound("product.notFound", id));
    }

    @Override
    public Map<Long, ProductView> findAll(Collection<Long> ids) {
        return repo.findByIds(ids);
    }

    @Override
    public List<ProductView> match(String name, int limit) {
        String n = normalize(name);
        return n.isEmpty() ? List.of() : repo.match(n, limit);
    }

    @Override
    @Transactional
    public ProductView findOrCreate(ProductDraft draft) {
        var row = toRow(draft);
        return repo.findByKey(row.normalizedName(), row.brandKey(), row.specKey()).orElseGet(() -> insert(row));
    }

    @Transactional
    public ProductView create(ProductDraft draft) {
        var row = toRow(draft);
        if (repo.findByKey(row.normalizedName(), row.brandKey(), row.specKey()).isPresent()) {
            throw ApiException.conflict("product.conflict", draft.name());
        }
        return insert(row);
    }

    @Transactional
    public ProductView update(long id, ProductDraft draft) {
        require(id);
        var row = toRow(draft);
        repo.findByKey(row.normalizedName(), row.brandKey(), row.specKey()).filter(p -> p.id() != id)
                .ifPresent(p -> {
                    throw ApiException.conflict("product.conflict", draft.name());
                });
        repo.update(id, row, System.currentTimeMillis());
        repo.replaceTags(id, row.tags());
        var saved = require(id);
        events.publishEvent(new ProductChangedEvent(saved));
        return saved;
    }

    public PageResult<ProductView> search(String q, String brand, String category, String tag, int page, int size) {
        int limit = PageResult.clampSize(size);
        var filter = new ProductRepository.Filter(normalize(q), blankToNull(brand), blankToNull(category),
                blankToNull(tag));
        return new PageResult<>(repo.search(filter, Math.max(page, 0) * limit, limit), repo.count(filter));
    }

    public List<String> brands(String q, int limit) {
        return repo.brands(like(q), Math.min(Math.max(limit, 1), 50));
    }

    public List<String> tags(String q, int limit) {
        return repo.tags(like(q), Math.min(Math.max(limit, 1), 50));
    }

    private ProductView insert(ProductRepository.Row row) {
        long id = repo.insert(row, System.currentTimeMillis());
        repo.replaceTags(id, row.tags());
        var saved = require(id);
        events.publishEvent(new ProductChangedEvent(saved));
        return saved;
    }

    private ProductRepository.Row toRow(ProductDraft d) {
        String name = d.name() == null ? "" : d.name().strip();
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            throw ApiException.invalidField("name", "validation.required");
        }
        String category = blankToNull(d.category()) == null ? ProductCategory.OTHER.name() : d.category().strip();
        if (!ProductCategory.isValid(category)) {
            throw ApiException.invalidField("category", "validation.range");
        }
        var spec = SpecParser.derive(d.specText(), d.specValue(), d.specUnit());
        if (spec == null) {
            throw ApiException.invalidField("specValue", "product.spec.invalid");
        }
        var tags = new LinkedHashSet<String>();
        if (d.tags() != null) {
            d.tags().stream().map(t -> t == null ? "" : t.strip().toLowerCase(java.util.Locale.ROOT))
                    .filter(t -> !t.isEmpty()).forEach(tags::add);
        }
        if (tags.size() > MAX_TAGS) {
            throw ApiException.invalidField("tags", "product.tags.tooMany");
        }
        String brand = blankToNull(d.brand());
        return new ProductRepository.Row(name, normalized, brand, normalize(brand), category, spec.text(), spec.key(),
                spec.value(), spec.unit(), spec.dim(), spec.baseQty(), blankToNull(d.barcode()),
                List.copyOf(tags));
    }

    private static String like(String q) {
        String s = q == null ? "" : q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + s + "%";
    }
}
