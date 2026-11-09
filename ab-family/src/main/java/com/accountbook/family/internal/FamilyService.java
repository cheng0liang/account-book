package com.accountbook.family.internal;

import com.accountbook.common.web.ApiException;
import com.accountbook.family.api.FamilyApi;
import com.accountbook.user.api.UserApi;
import com.accountbook.user.api.UserView;
import com.accountbook.user.api.event.UserDeletedEvent;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyService implements FamilyApi {

    static final String OWNER = "OWNER";
    static final String MEMBER = "MEMBER";
    static final int NAME_MAX = 30;
    static final int MAX_MEMBERS = 20;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FamilyRepository repo;
    private final UserApi users;

    FamilyService(FamilyRepository repo, UserApi users) {
        this.repo = repo;
        this.users = users;
    }

    /** Account deletion: members just leave, a sole owner takes the family with them, an owner with others is blocked. */
    @EventListener
    void on(UserDeletedEvent e) {
        repo.familyIdOf(e.userId()).ifPresent(familyId -> {
            var family = repo.find(familyId).orElseThrow();
            if (family.ownerId() != e.userId()) {
                repo.deleteMember(familyId, e.userId());
            } else if (repo.memberCount(familyId) > 1) {
                throw ApiException.conflict("family.ownerCannotDelete");
            } else {
                repo.deleteFamily(familyId);
            }
        });
    }

    @Override
    public Optional<Long> familyIdOf(long userId) {
        return repo.familyIdOf(userId);
    }

    @Override
    public List<Long> memberUserIds(long userId) {
        return repo.familyIdOf(userId)
                .map(f -> repo.members(f).stream().map(FamilyRepository.MemberRow::userId).toList())
                .orElse(List.of(userId));
    }

    public Optional<Views.Family> viewOf(long userId) {
        return repo.familyIdOf(userId).flatMap(repo::find).map(f -> view(f, userId));
    }

    @Transactional
    public Views.Family create(long userId, String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > NAME_MAX) {
            throw ApiException.invalidField("name", "family.name.invalid", NAME_MAX);
        }
        requireNoFamily(userId);
        long now = System.currentTimeMillis();
        long id = repo.insertFamily(clean, userId, newInviteCode(), now);
        repo.insertMember(id, userId, OWNER, now);
        return view(repo.find(id).orElseThrow(), userId);
    }

    @Transactional
    public Views.Family join(long userId, String code) {
        String normalized = code == null ? "" : code.strip().toUpperCase();
        var family = repo.findByInviteCode(normalized)
                .orElseThrow(() -> ApiException.notFound("family.inviteCode.invalid"));
        Optional<Long> current = repo.familyIdOf(userId);
        if (current.isPresent()) {
            if (current.get() == family.id()) {
                return view(family, userId);
            }
            throw ApiException.conflict("family.alreadyMember");
        }
        if (repo.memberCount(family.id()) >= MAX_MEMBERS) {
            throw ApiException.conflict("family.full", MAX_MEMBERS);
        }
        repo.insertMember(family.id(), userId, MEMBER, System.currentTimeMillis());
        return view(family, userId);
    }

    /** The owner can only leave as the last member, which dissolves the family. */
    @Transactional
    public void leave(long userId) {
        long familyId = repo.familyIdOf(userId).orElseThrow(() -> ApiException.notFound("family.notMember"));
        var family = repo.find(familyId).orElseThrow();
        if (family.ownerId() == userId) {
            if (repo.memberCount(familyId) > 1) {
                throw ApiException.conflict("family.ownerCannotLeave");
            }
            repo.deleteFamily(familyId);
        } else {
            repo.deleteMember(familyId, userId);
        }
    }

    @Transactional
    public void dissolve(long userId) {
        repo.deleteFamily(ownedFamily(userId).id());
    }

    @Transactional
    public void removeMember(long ownerId, long targetUserId) {
        var family = ownedFamily(ownerId);
        if (targetUserId == ownerId) {
            throw ApiException.conflict("family.ownerCannotLeave");
        }
        if (!repo.deleteMember(family.id(), targetUserId)) {
            throw ApiException.notFound("family.member.notFound");
        }
    }

    @Transactional
    public Views.Family resetInviteCode(long ownerId) {
        var family = ownedFamily(ownerId);
        repo.updateInviteCode(family.id(), newInviteCode());
        return view(repo.find(family.id()).orElseThrow(), ownerId);
    }

    private FamilyRepository.FamilyRow ownedFamily(long userId) {
        long familyId = repo.familyIdOf(userId).orElseThrow(() -> ApiException.notFound("family.notMember"));
        var family = repo.find(familyId).orElseThrow();
        if (family.ownerId() != userId) {
            throw ApiException.forbidden("family.ownerOnly");
        }
        return family;
    }

    private void requireNoFamily(long userId) {
        if (repo.familyIdOf(userId).isPresent()) {
            throw ApiException.conflict("family.alreadyMember");
        }
    }

    private String newInviteCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (repo.inviteCodeTaken(code));
        return code;
    }

    private Views.Family view(FamilyRepository.FamilyRow family, long viewerId) {
        var rows = repo.members(family.id());
        Map<Long, UserView> profiles = users.findAll(rows.stream().map(FamilyRepository.MemberRow::userId).toList());
        var members = rows.stream().map(r -> {
            UserView u = profiles.get(r.userId());
            return new Views.Member(r.userId(), u == null ? "User" + r.userId() : u.nickname(),
                    u == null ? null : u.avatarUrl(), r.role(), r.joinedAt(), r.userId() == viewerId);
        }).toList();
        String role = family.ownerId() == viewerId ? OWNER : MEMBER;
        return new Views.Family(family.id(), family.name(), family.inviteCode(), role, family.createdAt(), members);
    }
}
