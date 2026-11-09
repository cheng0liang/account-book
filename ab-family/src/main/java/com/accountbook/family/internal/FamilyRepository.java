package com.accountbook.family.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class FamilyRepository {

    record FamilyRow(long id, String name, long ownerId, String inviteCode, long createdAt) {
    }

    record MemberRow(long userId, String role, long joinedAt) {
    }

    private static final RowMapper<FamilyRow> FAMILY = (rs, i) -> new FamilyRow(rs.getLong("id"),
            rs.getString("name"), rs.getLong("owner_id"), rs.getString("invite_code"), rs.getLong("created_at"));

    private final JdbcClient jdbc;

    FamilyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Long> familyIdOf(long userId) {
        return jdbc.sql("SELECT family_id FROM fam_member WHERE user_id = :u").param("u", userId)
                .query(Long.class).optional();
    }

    Optional<FamilyRow> find(long id) {
        return jdbc.sql("SELECT id, name, owner_id, invite_code, created_at FROM fam_family WHERE id = :id")
                .param("id", id).query(FAMILY).optional();
    }

    Optional<FamilyRow> findByInviteCode(String code) {
        return jdbc.sql("SELECT id, name, owner_id, invite_code, created_at FROM fam_family WHERE invite_code = :c")
                .param("c", code).query(FAMILY).optional();
    }

    boolean inviteCodeTaken(String code) {
        return jdbc.sql("SELECT COUNT(*) FROM fam_family WHERE invite_code = :c").param("c", code)
                .query(Long.class).single() > 0;
    }

    long insertFamily(String name, long ownerId, String inviteCode, long now) {
        return jdbc.sql("""
                INSERT INTO fam_family (name, owner_id, invite_code, created_at)
                VALUES (:name, :owner, :code, :now) RETURNING id""")
                .param("name", name).param("owner", ownerId).param("code", inviteCode).param("now", now)
                .query(Long.class).single();
    }

    void insertMember(long familyId, long userId, String role, long now) {
        jdbc.sql("INSERT INTO fam_member (family_id, user_id, role, joined_at) VALUES (:f, :u, :r, :now)")
                .param("f", familyId).param("u", userId).param("r", role).param("now", now).update();
    }

    List<MemberRow> members(long familyId) {
        return jdbc.sql("SELECT user_id, role, joined_at FROM fam_member WHERE family_id = :f "
                        + "ORDER BY CASE role WHEN 'OWNER' THEN 0 ELSE 1 END, joined_at, user_id")
                .param("f", familyId)
                .query((rs, i) -> new MemberRow(rs.getLong("user_id"), rs.getString("role"), rs.getLong("joined_at")))
                .list();
    }

    long memberCount(long familyId) {
        return jdbc.sql("SELECT COUNT(*) FROM fam_member WHERE family_id = :f").param("f", familyId)
                .query(Long.class).single();
    }

    boolean deleteMember(long familyId, long userId) {
        return jdbc.sql("DELETE FROM fam_member WHERE family_id = :f AND user_id = :u")
                .param("f", familyId).param("u", userId).update() > 0;
    }

    void deleteFamily(long familyId) {
        jdbc.sql("DELETE FROM fam_member WHERE family_id = :f").param("f", familyId).update();
        jdbc.sql("DELETE FROM fam_family WHERE id = :f").param("f", familyId).update();
    }

    void updateInviteCode(long familyId, String code) {
        jdbc.sql("UPDATE fam_family SET invite_code = :c WHERE id = :f").param("c", code).param("f", familyId)
                .update();
    }
}
