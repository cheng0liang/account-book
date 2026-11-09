package com.accountbook.user.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface UserApi {

    Optional<UserView> find(long id);

    Map<Long, UserView> findAll(Collection<Long> ids);
}
