package com.yugjain.coursetracker;

import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small helpers shared by the API controllers. */
public final class ApiUtil {

    private ApiUtil() {}

    /** Like Python's data.get(key, default). */
    public static String str(Map<String, Object> data, String key, String defaultValue) {
        if (data == null) {
            return defaultValue;
        }
        Object v = data.get(key);
        return v == null ? defaultValue : v.toString();
    }

    public static ResponseEntity<Map<String, Object>> error(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }

    /** User row without the password hash. */
    public static Map<String, Object> safeUser(Map<String, Object> user) {
        Map<String, Object> copy = new LinkedHashMap<>(user);
        copy.remove("password_hash");
        return copy;
    }
}
