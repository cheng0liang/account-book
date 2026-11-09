package com.accountbook.user.internal;

/** Device position sent with a login; {@code locatedAt} is the client's fix time (UTC epoch ms). */
record LocationInput(Double latitude, Double longitude, Double accuracy, Long locatedAt) {

    boolean valid() {
        return latitude != null && longitude != null
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
                && !latitude.isNaN() && !longitude.isNaN();
    }
}
