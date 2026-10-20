package com.yugjain.coursetracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.yugjain.coursetracker.ApiUtil.*;

/** Modules API (list, create, get, update, toggle, delete). */
@RestController
public class ModuleController {

    private final Database db;
    private final ObjectMapper mapper = new ObjectMapper();

    public ModuleController(Database db) {
        this.db = db;
    }

    /** Python-style truthiness: True / non-zero / non-empty. */
    private static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof CharSequence cs) return cs.length() > 0;
        if (v instanceof Collection<?> c) return !c.isEmpty();
        if (v instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }

    private static int flag(Map<String, Object> data, String key) {
        return truthy(data == null ? null : data.get(key)) ? 1 : 0;
    }

    /** reference_links arrives as a JSON list; the database stores it as a JSON string. */
    private String linksJson(Map<String, Object> data) {
        Object v = data == null ? null : data.get("reference_links");
        if (v == null) return "[]";
        if (v instanceof String s) return s.isBlank() ? "[]" : s;
        try {
            return mapper.writeValueAsString(v);
        } catch (Exception e) {
            return "[]";
        }
    }

    @GetMapping("/api/courses/{cid}/modules")
    public ResponseEntity<Map<String, Object>> list(@PathVariable long cid, HttpSession session) {
        long userId = SessionUser.id(session);
        Map<String, Object> course = db.getCourseById(userId, cid);
        if (course == null) {
            return error(404, "Course not found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("course", course);
        body.put("modules", db.getCourseModules(userId, cid));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/api/courses/{cid}/modules")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable long cid,
            @RequestBody(required = false) Map<String, Object> data,
            HttpSession session) {
        long userId = SessionUser.id(session);
        if (db.getCourseById(userId, cid) == null) {
            return error(404, "Course not found");
        }
        String title = str(data, "title", "").strip();
        if (title.isEmpty()) {
            return error(400, "Module title is required.");
        }

        long mid = db.addModule(userId, cid, title, null,
            str(data, "notes", "").strip(), linksJson(data),
            flag(data, "is_important"), flag(data, "is_weak"),
            str(data, "key_points", "").strip());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("module", db.getModuleById(userId, mid));
        body.put("course", db.getCourseById(userId, cid));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/api/modules/{mid}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable long mid, HttpSession session) {
        Map<String, Object> mod = db.getModuleById(SessionUser.id(session), mid);
        if (mod == null) {
            return error(404, "Module not found");
        }
        return ResponseEntity.ok(mod);
    }

    @PutMapping("/api/modules/{mid}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable long mid,
            @RequestBody(required = false) Map<String, Object> data,
            HttpSession session) {
        long userId = SessionUser.id(session);
        if (db.getModuleById(userId, mid) == null) {
            return error(404, "Module not found");
        }
        String title = str(data, "title", "").strip();
        if (title.isEmpty()) {
            return error(400, "Module title is required.");
        }

        db.updateModule(userId, mid, title,
            str(data, "notes", "").strip(), linksJson(data),
            flag(data, "is_important"), flag(data, "is_weak"),
            str(data, "key_points", "").strip());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("module", db.getModuleById(userId, mid));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/api/modules/{mid}/toggle")
    public ResponseEntity<Map<String, Object>> toggle(
            @PathVariable long mid,
            @RequestBody(required = false) Map<String, Object> data,
            HttpSession session) {
        long userId = SessionUser.id(session);
        if (db.getModuleById(userId, mid) == null) {
            return error(404, "Module not found");
        }
        Map<String, Object> res = db.toggleModuleCompletion(userId, mid, flag(data, "is_completed"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.putAll(res);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/api/modules/{mid}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable long mid, HttpSession session) {
        long userId = SessionUser.id(session);
        Map<String, Object> mod = db.getModuleById(userId, mid);
        if (mod == null) {
            return error(404, "Module not found");
        }
        long cid = ((Number) mod.get("course_id")).longValue();
        db.deleteModule(userId, mid);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("course", db.getCourseById(userId, cid));
        return ResponseEntity.ok(body);
    }
}
