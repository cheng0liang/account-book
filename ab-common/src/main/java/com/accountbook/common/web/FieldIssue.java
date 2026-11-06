package com.accountbook.common.web;

public record FieldIssue(String field, String messageKey, Object[] args) {
}
