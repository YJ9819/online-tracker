package com.yugjain.coursetracker;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.yugjain.coursetracker.ApiUtil.*;

/** Study logs API. */
@RestController
@RequestMapping("/api/study-logs")
public class StudyLogController {

    private final Database db;

    public StudyLogController(Database db) {
        this.db = db;
    }

    private static long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).strip());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).strip());
        } catch (Exception e) {
            return 0;
        }
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(name = "course_id", required = false) Long courseId, HttpSession session) {
        return db.getStudyLogs(SessionUser.id(session), courseId);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody(required = false) Map<String, Object> data, HttpSession session) {
        long userId = SessionUser.id(session);
        long cid = toLong(data == null ? null : data.get("course_id"));
        String studyDate = str(data, "study_date", LocalDate.now().toString());
        double hours = toDouble(data == null ? null : data.get("hours_spent"));
        String topic = str(data, "topic_covered", "").strip();

        if (cid <= 0 || hours <= 0) {
            return error(400, "Valid course and positive hours are required.");
        }
        if (db.getCourseById(userId, cid) == null) {
            return error(404, "Course not found in your catalog.");
        }
        if (db.studyLogExists(userId, cid, studyDate, topic)) {
            return error(400, "A study session with this topic has already been logged for this date.");
        }

        long lid = db.addStudyLog(userId, cid, studyDate, hours, topic);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("log_id", lid);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{lid}")
    public Map<String, Object> delete(@PathVariable long lid, HttpSession session) {
        db.deleteStudyLog(SessionUser.id(session), lid);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        return body;
    }
}
