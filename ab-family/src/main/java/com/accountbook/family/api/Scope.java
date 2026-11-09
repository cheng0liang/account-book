package com.accountbook.family.api;

import java.util.List;

/** Whose data a read covers: just the caller's, or every member of the caller's family. */
public enum Scope {
    ME,
    FAMILY;

    /** Anything other than "family" (case-insensitive) means the caller only. */
    public static Scope parse(String value) {
        return "family".equalsIgnoreCase(value) ? FAMILY : ME;
    }

    public List<Long> userIds(FamilyApi families, long userId) {
        return this == FAMILY ? families.memberUserIds(userId) : List.of(userId);
    }
}
