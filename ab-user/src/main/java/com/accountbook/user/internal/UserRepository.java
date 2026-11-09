package com.accountbook.user.internal;

import static com.accountbook.common.db.Rows.nullableDouble;
import static com.accountbook.common.db.Rows.nullableLong;

import com.accountbook.user.internal.oauth.OAuthProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class UserRepository {

    private static final String USER_COLUMNS =
            "id, nickname, phone, gender, address, birth_date, country, avatar_url, status, created_at, last_login_at";

    record UserRow(long id, String nickname, String phone, String gender, String address, String birthDate,
                   String country, String avatarUrl, String status, long createdAt, Long lastLoginAt) {

        Views.Profile toProfile() {
            return new Views.Profile(id, nickname, phone, gender, address, birthDate, country, avatarUrl, createdAt,
                    lastLoginAt);
        }
    }

    record AccountRow(long userId, String provider, String providerUid) {
    }

    record SmsRow(String codeHash, long expiresAt) {
    }

    record StateRow(String provider, Long bindUserId, long createdAt) {
    }

    record TicketRow(long userId, String provider, long expiresAt) {
    }

    private static final RowMapper<UserRow> USER_MAPPER = (rs, i) -> new UserRow(
            rs.getLong("id"), rs.getString("nickname"), rs.getString("phone"), rs.getString("gender"),
            rs.getString("address"), rs.getString("birth_date"), rs.getString("country"),
            rs.getString("avatar_url"), rs.getString("status"), rs.getLong("created_at"),
            nullableLong(rs, "last_login_at"));

    private final JdbcClient jdbc;

    UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- users

    Optional<UserRow> findUser(long id) {
        return jdbc.sql("SELECT " + USER_COLUMNS + " FROM usr_user WHERE id = :id")
                .param("id", id).query(USER_MAPPER).optional();
    }

    List<UserRow> findUsers(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + USER_COLUMNS + " FROM usr_user WHERE id IN (:ids)")
                .param("ids", ids).query(USER_MAPPER).list();
    }

    long countUsers() {
        return jdbc.sql("SELECT COUNT(*) FROM usr_user").query(Long.class).single();
    }

    long insertUser(String nickname, String phone, String avatarUrl, long now) {
        return jdbc.sql("""
                INSERT INTO usr_user (nickname, phone, avatar_url, created_at, updated_at)
                VALUES (:nickname, :phone, :avatar, :now, :now) RETURNING id""")
                .param("nickname", nickname).param("phone", phone).param("avatar", avatarUrl).param("now", now)
                .query(Long.class).single();
    }

    void updateNickname(long id, String nickname, long now) {
        jdbc.sql("UPDATE usr_user SET nickname = :n, updated_at = :now WHERE id = :id")
                .param("n", nickname).param("now", now).param("id", id).update();
    }

    void updateProfile(long id, String nickname, String gender, String address, String birthDate, String country,
                       long now) {
        jdbc.sql("""
                UPDATE usr_user SET nickname = :nickname, gender = :gender, address = :address,
                       birth_date = :birth, country = :country, updated_at = :now WHERE id = :id""")
                .param("nickname", nickname).param("gender", gender).param("address", address)
                .param("birth", birthDate).param("country", country).param("now", now).param("id", id).update();
    }

    void touchLogin(long id, long now) {
        jdbc.sql("UPDATE usr_user SET last_login_at = :now WHERE id = :id").param("now", now).param("id", id).update();
    }

    // ---- login identities

    Optional<AccountRow> findAccount(String provider, String uid) {
        return jdbc.sql("SELECT user_id, provider, provider_uid FROM usr_auth_account "
                        + "WHERE provider = :p AND provider_uid = :uid")
                .param("p", provider).param("uid", uid)
                .query((rs, i) -> new AccountRow(rs.getLong("user_id"), rs.getString("provider"),
                        rs.getString("provider_uid")))
                .optional();
    }

    void insertAccount(long userId, String provider, OAuthProfile p, long now) {
        jdbc.sql("""
                INSERT INTO usr_auth_account (user_id, provider, provider_uid, union_id, nickname, email, avatar_url,
                                              raw_profile, created_at, last_login_at)
                VALUES (:uid, :provider, :puid, :union, :nick, :email, :avatar, :raw, :now, :now)""")
                .param("uid", userId).param("provider", provider).param("puid", p.subject())
                .param("union", p.unionId()).param("nick", p.nickname()).param("email", p.email())
                .param("avatar", p.avatarUrl()).param("raw", p.rawJson()).param("now", now).update();
    }

    void refreshAccount(String provider, OAuthProfile p, long now) {
        jdbc.sql("""
                UPDATE usr_auth_account SET union_id = COALESCE(:union, union_id), nickname = COALESCE(:nick, nickname),
                       email = COALESCE(:email, email), avatar_url = COALESCE(:avatar, avatar_url),
                       raw_profile = COALESCE(:raw, raw_profile), last_login_at = :now
                WHERE provider = :provider AND provider_uid = :puid""")
                .param("union", p.unionId()).param("nick", p.nickname()).param("email", p.email())
                .param("avatar", p.avatarUrl()).param("raw", p.rawJson()).param("now", now)
                .param("provider", provider).param("puid", p.subject()).update();
    }

    List<Views.Account> accountsOf(long userId) {
        return jdbc.sql("""
                SELECT provider, nickname, email, avatar_url, created_at, last_login_at FROM usr_auth_account
                WHERE user_id = :id ORDER BY created_at""")
                .param("id", userId)
                .query((rs, i) -> new Views.Account(rs.getString("provider"), rs.getString("nickname"),
                        rs.getString("email"), rs.getString("avatar_url"), rs.getLong("created_at"),
                        rs.getLong("last_login_at")))
                .list();
    }

    // ---- login locations

    void insertLocation(long userId, String provider, LocationInput l, long locatedAt, ClientInfo c, long now) {
        jdbc.sql("""
                INSERT INTO usr_login_location (user_id, provider, latitude, longitude, accuracy_m, located_at, ip,
                                                user_agent, created_at)
                VALUES (:uid, :provider, :lat, :lng, :acc, :at, :ip, :ua, :now)""")
                .param("uid", userId).param("provider", provider).param("lat", l.latitude())
                .param("lng", l.longitude()).param("acc", l.accuracy()).param("at", locatedAt)
                .param("ip", c.ip()).param("ua", c.userAgent()).param("now", now).update();
    }

    List<Views.Location> locationsOf(long userId, int limit) {
        return jdbc.sql("""
                SELECT id, provider, latitude, longitude, accuracy_m, located_at, ip FROM usr_login_location
                WHERE user_id = :id ORDER BY located_at DESC, id DESC LIMIT :limit""")
                .param("id", userId).param("limit", limit)
                .query((rs, i) -> new Views.Location(rs.getLong("id"), rs.getString("provider"),
                        rs.getDouble("latitude"), rs.getDouble("longitude"), nullableDouble(rs, "accuracy_m"),
                        rs.getLong("located_at"), rs.getString("ip")))
                .list();
    }

    // ---- sessions

    void insertSession(String tokenHash, long userId, String provider, String userAgent, long now, long expiresAt) {
        jdbc.sql("""
                INSERT INTO usr_session (token_hash, user_id, provider, user_agent, created_at, expires_at, last_seen_at)
                VALUES (:h, :uid, :provider, :ua, :now, :exp, :now)""")
                .param("h", tokenHash).param("uid", userId).param("provider", provider).param("ua", userAgent)
                .param("now", now).param("exp", expiresAt).update();
    }

    record SessionRow(long userId, long lastSeenAt) {
    }

    Optional<SessionRow> findSession(String tokenHash, long now) {
        return jdbc.sql("""
                SELECT s.user_id, s.last_seen_at FROM usr_session s JOIN usr_user u ON u.id = s.user_id
                WHERE s.token_hash = :h AND s.expires_at > :now AND u.status = 'ACTIVE'""")
                .param("h", tokenHash).param("now", now)
                .query((rs, i) -> new SessionRow(rs.getLong("user_id"), rs.getLong("last_seen_at")))
                .optional();
    }

    void touchSession(String tokenHash, long now) {
        jdbc.sql("UPDATE usr_session SET last_seen_at = :now WHERE token_hash = :h")
                .param("now", now).param("h", tokenHash).update();
    }

    void deleteSession(String tokenHash) {
        jdbc.sql("DELETE FROM usr_session WHERE token_hash = :h").param("h", tokenHash).update();
    }

    int purgeExpired(long now, long stateCutoff) {
        int n = jdbc.sql("DELETE FROM usr_session WHERE expires_at <= :now").param("now", now).update();
        n += jdbc.sql("DELETE FROM usr_sms_code WHERE expires_at <= :now").param("now", now).update();
        n += jdbc.sql("DELETE FROM usr_login_ticket WHERE expires_at <= :now").param("now", now).update();
        n += jdbc.sql("DELETE FROM usr_oauth_state WHERE created_at <= :cut").param("cut", stateCutoff).update();
        return n;
    }

    // ---- sms codes

    Optional<Long> smsSentAt(String phone) {
        return jdbc.sql("SELECT sent_at FROM usr_sms_code WHERE phone = :p").param("p", phone)
                .query(Long.class).optional();
    }

    void upsertSms(String phone, String codeHash, long now, long expiresAt) {
        jdbc.sql("""
                INSERT INTO usr_sms_code (phone, code_hash, attempts, sent_at, expires_at)
                VALUES (:p, :h, 0, :now, :exp)
                ON CONFLICT (phone) DO UPDATE SET code_hash = :h, attempts = 0, sent_at = :now, expires_at = :exp""")
                .param("p", phone).param("h", codeHash).param("now", now).param("exp", expiresAt).update();
    }

    /** Counts one attempt and returns the stored code, or empty when there is none or attempts are exhausted. */
    Optional<SmsRow> takeSmsAttempt(String phone, int maxAttempts) {
        return jdbc.sql("""
                UPDATE usr_sms_code SET attempts = attempts + 1 WHERE phone = :p AND attempts < :max
                RETURNING code_hash, expires_at""")
                .param("p", phone).param("max", maxAttempts)
                .query((rs, i) -> new SmsRow(rs.getString("code_hash"), rs.getLong("expires_at"))).optional();
    }

    boolean smsExists(String phone) {
        return jdbc.sql("SELECT COUNT(*) FROM usr_sms_code WHERE phone = :p").param("p", phone)
                .query(Long.class).single() > 0;
    }

    void deleteSms(String phone) {
        jdbc.sql("DELETE FROM usr_sms_code WHERE phone = :p").param("p", phone).update();
    }

    // ---- oauth state and login tickets

    void insertState(String state, String provider, Long bindUserId, long now) {
        jdbc.sql("INSERT INTO usr_oauth_state (state, provider, bind_user_id, created_at) VALUES (:s, :p, :b, :now)")
                .param("s", state).param("p", provider).param("b", bindUserId).param("now", now).update();
    }

    Optional<StateRow> consumeState(String state) {
        return jdbc.sql("DELETE FROM usr_oauth_state WHERE state = :s RETURNING provider, bind_user_id, created_at")
                .param("s", state)
                .query((rs, i) -> new StateRow(rs.getString("provider"), nullableLong(rs, "bind_user_id"),
                        rs.getLong("created_at")))
                .optional();
    }

    void insertTicket(String ticketHash, long userId, String provider, long expiresAt) {
        jdbc.sql("INSERT INTO usr_login_ticket (ticket_hash, user_id, provider, expires_at) VALUES (:h, :u, :p, :e)")
                .param("h", ticketHash).param("u", userId).param("p", provider).param("e", expiresAt).update();
    }

    Optional<TicketRow> consumeTicket(String ticketHash) {
        return jdbc.sql("DELETE FROM usr_login_ticket WHERE ticket_hash = :h RETURNING user_id, provider, expires_at")
                .param("h", ticketHash)
                .query((rs, i) -> new TicketRow(rs.getLong("user_id"), rs.getString("provider"),
                        rs.getLong("expires_at")))
                .optional();
    }
}
