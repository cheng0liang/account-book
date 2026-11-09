package com.accountbook.family.internal;

import java.util.List;

final class Views {

    private Views() {
    }

    record Member(long userId, String nickname, String avatarUrl, String role, long joinedAt, boolean you) {
    }

    /** The caller's family. {@code role} is the caller's own role. */
    record Family(long id, String name, String inviteCode, String role, long createdAt, List<Member> members) {
    }
}
