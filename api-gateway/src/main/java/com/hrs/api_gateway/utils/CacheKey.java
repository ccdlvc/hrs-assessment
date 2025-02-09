package com.hrs.api_gateway.utils;

public enum CacheKey {
    HOTEL_BY_ID("hotel:");

    private final String prefix;

    CacheKey(String prefix) {
        this.prefix = prefix;
    }

    public String getKey(String identifier) {
        return prefix + identifier;
    }

    public String getKey(Long identifier) {
        return prefix + identifier.toString();
    }

    public String getPrefix() {
        return prefix;
    }
}
