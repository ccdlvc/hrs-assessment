package com.hrs.api_gateway.utils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class QueryParamExtractor {

    public static Map<String, String> getQueryParameters(URI uri) {
        Map<String, String> queryParams = new HashMap<>();
        String query = uri.getQuery();

        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    String key = decodeValue(keyValue[0]);
                    String value = decodeValue(keyValue[1]);
                    queryParams.put(key, value);
                }
            }
        }

        return queryParams;
    }

    private static String decodeValue(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            // Handle decoding error (e.g., log it or return the original value)
            return value;
        }
    }
}
