package com.accountbook.family.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accountbook.common.testing.TestDb;
import com.accountbook.common.web.ApiException;
import com.accountbook.user.api.UserApi;
import com.accountbook.user.api.UserView;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FamilyServiceTest {

    private TestDb db;
    private FamilyService service;

    private static final long ALICE = 1;
    private static final long BOB = 2;
    private static final long CAROL = 3;

    @BeforeEach
    void setUp() {
        db = TestDb.create("family");
        UserApi users = new UserApi() {
            @Override
            public Optional<UserView> find(long id) {
                return Optional.of(new UserView(id, "user-" + id, null));
            }

            @Override
            public Map<Long, UserView> findAll(Collection<Long> ids) {
                Map<Long, UserView> out = new HashMap<>();
                ids.forEach(i -> out.put(i, new UserView(i, "user-" + i, null)));
                return out;
            }
        };
        service = new FamilyService(new FamilyRepository(db.jdbc()), users);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private static void assertStatus(Runnable call, HttpStatus status, String key) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(status);
            assertThat(e.messageKey()).isEqualTo(key);
        });
    }

    @Test
    void createMakesTheCallerOwnerWithAnInviteCode() {
        var f = service.create(ALICE, "  Home ");
        assertThat(f.name()).isEqualTo("Home");
        assertThat(f.role()).isEqualTo("OWNER");
        assertThat(f.inviteCode()).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(f.members()).singleElement().satisfies(m -> {
            assertThat(m.userId()).isEqualTo(ALICE);
            assertThat(m.you()).isTrue();
            assertThat(m.nickname()).isEqualTo("user-1");
        });
        assertThat(service.familyIdOf(ALICE)).contains(f.id());
    }

    @Test
    void nameIsValidatedAndOneFamilyPerUser() {
        assertThatThrownBy(() -> service.create(ALICE, " ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(ALICE, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(ALICE, "x".repeat(31))).isInstanceOf(ApiException.class);
        service.create(ALICE, "A");
        assertStatus(() -> service.create(ALICE, "B"), HttpStatus.CONFLICT, "family.alreadyMember");
    }

    @Test
    void joinByCodeIsCaseInsensitiveAndMembersShareTheSameScope() {
        var f = service.create(ALICE, "Home");
        var joined = service.join(BOB, " " + f.inviteCode().toLowerCase() + " ");
        assertThat(joined.role()).isEqualTo("MEMBER");
        assertThat(joined.members()).extracting(Views.Member::userId).containsExactly(ALICE, BOB);
        assertThat(joined.members()).filteredOn(Views.Member::you).extracting(Views.Member::userId).containsExactly(BOB);

        assertThat(service.memberUserIds(ALICE)).containsExactlyInAnyOrder(ALICE, BOB);
        assertThat(service.memberUserIds(BOB)).containsExactlyInAnyOrder(ALICE, BOB);
        assertThat(service.memberUserIds(CAROL)).containsExactly(CAROL);
        assertThat(service.familyIdOf(CAROL)).isEmpty();
    }

    @Test
    void joinRejectsBadCodesAndSecondFamiliesButIsIdempotentForTheSameFamily() {
        var a = service.create(ALICE, "A");
        var b = service.create(CAROL, "B");
        assertStatus(() -> service.join(BOB, "NOPE1234"), HttpStatus.NOT_FOUND, "family.inviteCode.invalid");
        assertStatus(() -> service.join(BOB, null), HttpStatus.NOT_FOUND, "family.inviteCode.invalid");

        service.join(BOB, a.inviteCode());
        assertThat(service.join(BOB, a.inviteCode()).members()).hasSize(2);
        assertStatus(() -> service.join(BOB, b.inviteCode()), HttpStatus.CONFLICT, "family.alreadyMember");
    }

    @Test
    void familyIsFullAtTheMemberLimit() {
        var f = service.create(1000, "Big");
        for (int i = 1; i < FamilyService.MAX_MEMBERS; i++) {
            service.join(1000 + i, f.inviteCode());
        }
        assertStatus(() -> service.join(5000, f.inviteCode()), HttpStatus.CONFLICT, "family.full");
    }

    @Test
    void memberCanLeaveButOwnerOnlyAsTheLastMemberWhichDissolves() {
        var f = service.create(ALICE, "Home");
        service.join(BOB, f.inviteCode());

        assertStatus(() -> service.leave(ALICE), HttpStatus.CONFLICT, "family.ownerCannotLeave");
        service.leave(BOB);
        assertThat(service.familyIdOf(BOB)).isEmpty();
        assertStatus(() -> service.leave(BOB), HttpStatus.NOT_FOUND, "family.notMember");

        service.leave(ALICE);
        assertThat(service.viewOf(ALICE)).isEmpty();
        assertThat(db.jdbc().sql("SELECT COUNT(*) FROM fam_family").query(Long.class).single()).isZero();
        assertThat(db.jdbc().sql("SELECT COUNT(*) FROM fam_member").query(Long.class).single()).isZero();
    }

    @Test
    void ownerCanKickAndDissolveButMembersCannot() {
        var f = service.create(ALICE, "Home");
        service.join(BOB, f.inviteCode());
        service.join(CAROL, f.inviteCode());

        assertStatus(() -> service.removeMember(BOB, CAROL), HttpStatus.FORBIDDEN, "family.ownerOnly");
        assertStatus(() -> service.dissolve(BOB), HttpStatus.FORBIDDEN, "family.ownerOnly");
        assertStatus(() -> service.resetInviteCode(BOB), HttpStatus.FORBIDDEN, "family.ownerOnly");
        assertStatus(() -> service.removeMember(ALICE, ALICE), HttpStatus.CONFLICT, "family.ownerCannotLeave");
        assertStatus(() -> service.removeMember(ALICE, 99), HttpStatus.NOT_FOUND, "family.member.notFound");

        service.removeMember(ALICE, CAROL);
        assertThat(service.memberUserIds(ALICE)).containsExactlyInAnyOrder(ALICE, BOB);
        assertThat(service.memberUserIds(CAROL)).containsExactly(CAROL);

        service.dissolve(ALICE);
        assertThat(service.memberUserIds(BOB)).containsExactly(BOB);
        assertThat(service.viewOf(ALICE)).isEmpty();
    }

    @Test
    void kickingSomeoneFromAnotherFamilyIsRefused() {
        service.create(ALICE, "A");
        var other = service.create(CAROL, "B");
        service.join(BOB, other.inviteCode());
        assertStatus(() -> service.removeMember(ALICE, BOB), HttpStatus.NOT_FOUND, "family.member.notFound");
        assertThat(service.familyIdOf(BOB)).isPresent();
    }

    @Test
    void resettingTheInviteCodeInvalidatesTheOldOne() {
        var f = service.create(ALICE, "Home");
        var reset = service.resetInviteCode(ALICE);
        assertThat(reset.inviteCode()).isNotEqualTo(f.inviteCode());
        assertStatus(() -> service.join(BOB, f.inviteCode()), HttpStatus.NOT_FOUND, "family.inviteCode.invalid");
        assertThat(service.join(BOB, reset.inviteCode()).members()).hasSize(2);
    }

    @Test
    void aUserCanStartOverAfterLeavingOrBeingRemoved() {
        var f = service.create(ALICE, "A");
        service.join(BOB, f.inviteCode());
        service.removeMember(ALICE, BOB);
        var mine = service.create(BOB, "Bob's");
        assertThat(mine.members()).hasSize(1);
        assertThat(service.memberUserIds(ALICE)).containsExactly(ALICE);
    }
}
