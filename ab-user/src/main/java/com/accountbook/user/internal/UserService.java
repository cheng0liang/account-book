package com.accountbook.user.internal;

import com.accountbook.common.web.ApiException;
import com.accountbook.user.api.UserApi;
import com.accountbook.user.api.UserView;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserApi {

    private static final Set<String> GENDERS = Set.of("UNKNOWN", "MALE", "FEMALE", "OTHER");
    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());
    private static final int NICKNAME_MAX = 30;
    private static final int ADDRESS_MAX = 200;

    private final UserRepository repo;

    UserService(UserRepository repo) {
        this.repo = repo;
    }

    record ProfileForm(String nickname, String gender, String address, String birthDate, String country) {
    }

    @Override
    public Optional<UserView> find(long id) {
        return repo.findUser(id).map(UserService::view);
    }

    @Override
    public Map<Long, UserView> findAll(Collection<Long> ids) {
        return repo.findUsers(ids).stream().map(UserService::view)
                .collect(Collectors.toMap(UserView::id, Function.identity()));
    }

    Views.Profile profile(long id) {
        return repo.findUser(id).orElseThrow(() -> ApiException.unauthorized("auth.required")).toProfile();
    }

    Views.Profile update(long id, ProfileForm form) {
        String nickname = form.nickname() == null ? "" : form.nickname().strip();
        if (nickname.isEmpty() || nickname.length() > NICKNAME_MAX) {
            throw ApiException.invalidField("nickname", "user.nickname.invalid", NICKNAME_MAX);
        }
        String gender = blankToNull(form.gender()) == null ? "UNKNOWN" : form.gender().strip().toUpperCase(Locale.ROOT);
        if (!GENDERS.contains(gender)) {
            throw ApiException.invalidField("gender", "user.gender.invalid");
        }
        String address = blankToNull(form.address());
        if (address != null && address.length() > ADDRESS_MAX) {
            throw ApiException.invalidField("address", "user.address.tooLong", ADDRESS_MAX);
        }
        String birth = blankToNull(form.birthDate());
        if (birth != null) {
            try {
                LocalDate d = LocalDate.parse(birth);
                if (d.isAfter(LocalDate.now()) || d.getYear() < 1900) {
                    throw ApiException.invalidField("birthDate", "user.birthDate.invalid");
                }
            } catch (java.time.format.DateTimeParseException e) {
                throw ApiException.invalidField("birthDate", "user.birthDate.invalid");
            }
        }
        String country = blankToNull(form.country()) == null ? null : form.country().strip().toUpperCase(Locale.ROOT);
        if (country != null && !COUNTRIES.contains(country)) {
            throw ApiException.invalidField("country", "user.country.invalid");
        }
        repo.updateProfile(id, nickname, gender, address, birth, country, System.currentTimeMillis());
        return profile(id);
    }

    List<Views.Account> accounts(long id) {
        return repo.accountsOf(id);
    }

    List<Views.Location> locations(long id, int limit) {
        return repo.locationsOf(id, Math.max(1, Math.min(limit, 200)));
    }

    private static UserView view(UserRepository.UserRow r) {
        return new UserView(r.id(), r.nickname(), r.avatarUrl());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
