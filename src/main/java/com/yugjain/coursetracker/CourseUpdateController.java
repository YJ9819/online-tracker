package com.yugjain.coursetracker;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.yugjain.coursetracker.ApiUtil.*;

/** PUT and DELETE for /api/courses/{cid}. */
@RestController
@RequestMapping("/api/courses")
public class CourseUpdateController {

    private final Database db;

    public CourseUpdateController(Database db) {
        this.db = db;
    }

    @PutMapping("/{cid}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable long cid,
            @RequestBody(required = false) Map<String, Object> data,
            HttpSession session) {
        long userId = SessionUser.id(session);
        Map<String, Object> course = db.getCourseById(userId, cid);
        if (course == null) {
            return error(404, "Course not found");
        }

        String name = str(data, "course_name", "").strip();
        String platform = str(data, "platform", "").strip();
        if (name.isEmpty() || platform.isEmpty()) {
            return error(400, "Course name and platform are required.");
        }
        if (db.courseExists(userId, name, cid)) {
            return error(400, "Another course with title '" + name + "' already exists.");
        }

        String currentStatus = course.get("status") == null ? "Not Started" : course.get("status").toString();
        db.updateCourse(userId, cid, name, platform,
            str(data, "instructor", "").strip(),
            str(data, "start_date", "").strip(),
            str(data, "end_date", "").strip(),
            str(data, "status", currentStatus),
            str(data, "priority", "Medium"),
            str(data, "course_link", "").strip(),
            str(data, "notes", "").strip());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("course", db.getCourseById(userId, cid));
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{cid}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable long cid, HttpSession session) {
        long userId = SessionUser.id(session);
        if (db.getCourseById(userId, cid) == null) {
            return error(404, "Course not found");
        }
        db.deleteCourse(userId, cid);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        return ResponseEntity.ok(body);
    }
}
