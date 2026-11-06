package com.accountbook.merchant.internal;

import static com.accountbook.common.text.NameNormalizer.blankToNull;
import static com.accountbook.common.text.NameNormalizer.normalize;

import com.accountbook.common.web.ApiException;
import com.accountbook.common.web.PageResult;
import com.accountbook.merchant.api.MerchantApi;
import com.accountbook.merchant.api.MerchantDraft;
import com.accountbook.merchant.api.MerchantType;
import com.accountbook.merchant.api.MerchantView;
import com.accountbook.merchant.api.event.MerchantChangedEvent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService implements MerchantApi {

    private final MerchantRepository repo;
    private final ApplicationEventPublisher events;

    MerchantService(MerchantRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    @Override
    public Optional<MerchantView> find(long id) {
        return repo.findById(id);
    }

    @Override
    public MerchantView require(long id) {
        return find(id).orElseThrow(() -> ApiException.notFound("merchant.notFound", id));
    }

    @Override
    public List<MerchantView> match(String name, int limit) {
        String n = normalize(name);
        return n.isEmpty() ? List.of() : repo.match(n, limit);
    }

    @Override
    @Transactional
    public MerchantView findOrCreate(MerchantDraft draft) {
        var row = toRow(draft);
        return repo.findByKey(row.normalizedName(), row.cityKey()).orElseGet(() -> insert(row));
    }

    @Transactional
    public MerchantView create(MerchantDraft draft) {
        var row = toRow(draft);
        if (repo.findByKey(row.normalizedName(), row.cityKey()).isPresent()) {
            throw ApiException.conflict("merchant.conflict", draft.name());
        }
        return insert(row);
    }

    @Transactional
    public MerchantView update(long id, MerchantDraft draft) {
        require(id);
        var row = toRow(draft);
        repo.findByKey(row.normalizedName(), row.cityKey()).filter(m -> m.id() != id).ifPresent(m -> {
            throw ApiException.conflict("merchant.conflict", draft.name());
        });
        repo.update(id, row, System.currentTimeMillis());
        var saved = require(id);
        events.publishEvent(new MerchantChangedEvent(saved));
        return saved;
    }

    public PageResult<MerchantView> search(String q, String country, String city, String district, String type,
                                           int page, int size) {
        int limit = PageResult.clampSize(size);
        var filter = new MerchantRepository.Filter(normalize(q), blankToNull(country) == null ? null
                : country.strip().toUpperCase(Locale.ROOT), blankToNull(city), blankToNull(district), blankToNull(type));
        return new PageResult<>(repo.search(filter, Math.max(page, 0) * limit, limit), repo.count(filter));
    }

    private MerchantView insert(MerchantRepository.Row row) {
        long id = repo.insert(row, System.currentTimeMillis());
        var saved = require(id);
        events.publishEvent(new MerchantChangedEvent(saved));
        return saved;
    }

    private MerchantRepository.Row toRow(MerchantDraft d) {
        String name = d.name().strip();
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            throw ApiException.invalidField("name", "validation.required");
        }
        String type = blankToNull(d.type()) == null ? MerchantType.OTHER.name() : d.type().strip();
        if (!MerchantType.isValid(type)) {
            throw ApiException.invalidField("type", "validation.range");
        }
        String country = blankToNull(d.country()) == null ? null : d.country().strip().toUpperCase(Locale.ROOT);
        String city = blankToNull(d.city());
        return new MerchantRepository.Row(name, normalized, country, city, normalize(city), blankToNull(d.district()),
                type, blankToNull(d.address()), d.latitude(), d.longitude());
    }
}
