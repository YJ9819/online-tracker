package com.yugjain.coursetracker;

import jakarta.servlet.http.HttpSession;

/** Reads the logged-in user's id from the session (Flask: session["user_id"]). */
public final class SessionUser {
    public static final String KEY = "user_id";

    private SessionUser() {}

    public static long id(HttpSession session) {
        return ((Number) session.getAttribute(KEY)).longValue();
    }
}
