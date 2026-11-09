package com.accountbook.family.internal;

import com.accountbook.user.api.CurrentUser;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/family")
class FamilyController {

    record CreateForm(String name) {
    }

    record JoinForm(String code) {
    }

    private final FamilyService families;

    FamilyController(FamilyService families) {
        this.families = families;
    }

    /** 204 when the caller has no family. */
    @GetMapping
    ResponseEntity<Views.Family> mine() {
        return families.viewOf(CurrentUser.id()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Views.Family create(@RequestBody CreateForm form) {
        return families.create(CurrentUser.id(), form == null ? null : form.name());
    }

    @PostMapping("/join")
    Views.Family join(@RequestBody JoinForm form) {
        return families.join(CurrentUser.id(), form == null ? null : form.code());
    }

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave() {
        families.leave(CurrentUser.id());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dissolve() {
        families.dissolve(CurrentUser.id());
    }

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@PathVariable long userId) {
        families.removeMember(CurrentUser.id(), userId);
    }

    @PostMapping("/invite-code/reset")
    Views.Family resetInviteCode() {
        return families.resetInviteCode(CurrentUser.id());
    }
}
