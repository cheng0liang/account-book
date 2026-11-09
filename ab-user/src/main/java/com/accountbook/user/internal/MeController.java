package com.accountbook.user.internal;

import com.accountbook.user.api.CurrentUser;
import com.accountbook.user.internal.UserService.ProfileForm;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
class MeController {

    private final UserService users;
    private final SessionService sessions;

    MeController(UserService users, SessionService sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @GetMapping
    Views.Profile me() {
        return users.profile(CurrentUser.id());
    }

    @PutMapping
    Views.Profile update(@RequestBody ProfileForm form) {
        return users.update(CurrentUser.id(), form);
    }

    @GetMapping("/accounts")
    List<Views.Account> accounts() {
        return users.accounts(CurrentUser.id());
    }

    @GetMapping("/locations")
    List<Views.Location> locations(@RequestParam(defaultValue = "20") int limit) {
        return users.locations(CurrentUser.id(), limit);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount() {
        users.deleteAccount(CurrentUser.id());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest http) {
        CurrentUser.id();
        sessions.logout(AuthInterceptor.bearer(http));
    }
}
