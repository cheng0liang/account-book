package com.accountbook.user.internal;

/** JSON shapes returned by the user endpoints. */
final class Views {

    private Views() {
    }

    record Profile(long id, String nickname, String phone, String gender, String address, String birthDate,
                   String country, String avatarUrl, long createdAt, Long lastLoginAt) {
    }

    record Account(String provider, String nickname, String email, String avatarUrl, long createdAt,
                   long lastLoginAt) {
    }

    record Location(long id, String provider, double latitude, double longitude, Double accuracyM, long locatedAt,
                    String ip) {
    }

    record Login(String token, long expiresAt, Profile user) {
    }
}
