package com.yugjain.coursetracker;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.yugjain.coursetracker.ApiUtil.*;

/** Register and login (Java port of /api/auth/register and /api/auth/login). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Database db;
    // BCrypt replaces Werkzeug's generate_password_hash / check_password_hash
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(Database db) {
        this.db = db;
    }

    private void startSession(HttpServletRequest request, long userId) {
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(SessionUser.KEY, userId);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody(required = false) Map<String, Object> data, HttpServletRequest request) {
        String username = str(data, "username", "").strip();
        String password = str(data, "password", "");
        String name = str(data, "name", "").strip();
        String subject = str(data, "subject", "").strip();
        String rollNo = str(data, "roll_no", "").strip();
        String email = str(data, "email", "").strip();
        String avatarColor = str(data, "avatar_color", "#4F46E5").strip();

        if (username.length() < 3) {
            return error(400, "Username must be at least 3 characters.");
        }
        if (password.length() < 4) {
            return error(400, "Password must be at least 4 characters.");
        }
        if (name.isEmpty()) {
            return error(400, "Full Name is required.");
        }
        if (subject.isEmpty()) {
            return error(400, "Subject / Grade is required.");
        }
        if (db.getUserByUsername(username) != null) {
            return error(409, "Username is already taken. Please choose another.");
        }
        if (!email.isEmpty() && db.getUserByUsername(email) != null) {
            return error(409, "An account with this email already exists.");
        }

        long userId = db.createUser(username, encoder.encode(password), name, subject, rollNo, email, avatarColor);
        startSession(request, userId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("user", safeUser(db.getUserById(userId)));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody(required = false) Map<String, Object> data, HttpServletRequest request) {
        String identifier = str(data, "username", "").strip();
        String password = str(data, "password", "");

        if (identifier.isEmpty() || password.isEmpty()) {
            return error(400, "Username and password are required.");
        }

        Map<String, Object> user = db.getUserByUsername(identifier);
        if (user == null || !encoder.matches(password, String.valueOf(user.get("password_hash")))) {
            return error(401, "Invalid username or password.");
        }

        startSession(request, ((Number) user.get("user_id")).longValue());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("user", safeUser(user));
        return ResponseEntity.ok(body);
    }
}
