package com.yugjain.coursetracker;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.yugjain.coursetracker.ApiUtil.*;

/** Courses API. (update / delete / modules come in the next step.) */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final Database db;

    public CourseController(Database db) {
        this.db = db;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String platform,
            HttpSession session) {
        return db.getCourses(SessionUser.id(session), search.strip(), status.strip(), platform.strip());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody(required = false) Map<String, Object> data, HttpSession session) {
        long userId = SessionUser.id(session);
        String name = str(data, "course_name", "").strip();
        String platform = str(data, "platform", "").strip();

        if (name.isEmpty() || platform.isEmpty()) {
            return error(400, "Course name and platform are required.");
        }
        if (db.courseExists(userId, name, null)) {
            return error(400, "You already have an active or tracked course titled '" + name + "'.");
        }

        long cid = db.addCourse(userId, name, platform,
            str(data, "instructor", "").strip(),
            str(data, "start_date", "").strip(),
            str(data, "end_date", "").strip(),
            str(data, "status", "Not Started"),
            0,
            str(data, "priority", "Medium"),
            str(data, "course_link", "").strip(),
            str(data, "notes", "").strip());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("course_id", cid);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{cid}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable long cid, HttpSession session) {
        Map<String, Object> course = db.getCourseById(SessionUser.id(session), cid);
        if (course == null) {
            return error(404, "Course not found");
        }
        return ResponseEntity.ok(course);
    }
}
