package com.yugjain.coursetracker;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.yugjain.coursetracker.ApiUtil.*;

/** /api/auth/logout, /api/auth/me and /api/auth/profile. */
@RestController
@RequestMapping("/api/auth")
public class SessionController {

    private final Database db;

    public SessionController(Database db) {
        this.db = db;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        return body;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(SessionUser.KEY);
        Map<String, Object> user = userId == null ? null : db.getUserById(((Number) userId).longValue());

        if (user == null) {
            if (session != null && userId != null) {
                session.invalidate();
            }
            body.put("authenticated", false);
            body.put("user", null);
            return body;
        }
        body.put("authenticated", true);
        body.put("user", safeUser(user));
        return body;
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestBody(required = false) Map<String, Object> data, HttpSession session) {
        long userId = SessionUser.id(session);
        String name = str(data, "name", "").strip();
        String subject = str(data, "subject", "").strip();
        String rollNo = str(data, "roll_no", "").strip();
        String email = str(data, "email", "").strip();
        String avatarColor = str(data, "avatar_color", "#4F46E5").strip();

        if (name.isEmpty() || subject.isEmpty()) {
            return error(400, "Name and Subject are required.");
        }

        db.updateUserProfile(userId, name, subject, rollNo, email, avatarColor);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("user", safeUser(db.getUserById(userId)));
        return ResponseEntity.ok(body);
    }
}
