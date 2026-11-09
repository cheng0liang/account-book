package com.accountbook.family.api;

import java.util.List;
import java.util.Optional;

public interface FamilyApi {

    /** The user's family id, if they belong to one. */
    Optional<Long> familyIdOf(long userId);

    /** Every member of the user's family, the user included; just the user when they have no family. */
    List<Long> memberUserIds(long userId);
}
