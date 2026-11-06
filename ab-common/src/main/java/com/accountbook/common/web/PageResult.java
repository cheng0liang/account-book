package com.accountbook.common.web;

import java.util.List;

public record PageResult<T>(List<T> items, long total) {

    public static final int MAX_SIZE = 100;

    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }
}
