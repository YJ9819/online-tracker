package com.yugjain.coursetracker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Database layer for Online Course Tracker (Java port of database.py).
 * SQLite + per-user data isolation + module-based progress + exam revision lists.
 * Every query is parameterized (?), so it is safe from SQL injection.
 * Rows are returned as Map<String, Object>, the same shape as the Python dicts.
 */
@Repository
public class Database {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public Database(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // Low-level helpers (same role as execute_query / fetch_all / fetch_one)
    // ------------------------------------------------------------------

    private int execute(String sql, Object... params) {
        return jdbc.update(sql, params);
    }

    /** INSERT that returns the new row id. */
    private long insert(String sql, Object... params) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        }, keys);
        Number key = keys.getKey();
        return key == null ? 0 : key.longValue();
    }

    private List<Map<String, Object>> fetchAll(String sql, Object... params) {
        return jdbc.queryForList(sql, params);
    }

    private Map<String, Object> fetchOne(String sql, Object... params) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static int toInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }

    private List<Map<String, Object>> parseLinks(Object raw) {
        try {
            String json = (raw == null || raw.toString().isBlank()) ? "[]" : raw.toString();
            return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Long> parseIdList(Object raw) {
        try {
            String json = (raw == null || raw.toString().isBlank()) ? "[]" : raw.toString();
            return mapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ==================================================================
    // 1. User authentication & profile
    // ==================================================================

    public long createUser(String username, String passwordHash, String name, String subject,
                           String rollNo, String email, String avatarColor) {
        return insert(
            "INSERT INTO users (username, password_hash, name, subject, roll_no, email, avatar_color) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
            s(username).toLowerCase(), passwordHash, s(name), s(subject), s(rollNo),
            s(email).toLowerCase(), avatarColor);
    }

    public Map<String, Object> getUserByUsername(String usernameOrEmail) {
        String val = s(usernameOrEmail).toLowerCase();
        return fetchOne("SELECT * FROM users WHERE username = ? OR (email != '' AND email = ?) LIMIT 1", val, val);
    }

    public Map<String, Object> getUserById(long userId) {
        return fetchOne("SELECT * FROM users WHERE user_id = ?", userId);
    }

    public int updateUserProfile(long userId, String name, String subject, String rollNo,
                                 String email, String avatarColor) {
        return execute(
            "UPDATE users SET name = ?, subject = ?, roll_no = ?, email = ?, avatar_color = ? WHERE user_id = ?",
            s(name), s(subject), s(rollNo), s(email).toLowerCase(), avatarColor, userId);
    }

    // ==================================================================
    // 2. Courses (per user)
    // ==================================================================

    /** Prevents duplicate course names for the same user. excludeId may be null. */
    public boolean courseExists(long userId, String courseName, Long excludeId) {
        String sql = "SELECT course_id FROM courses WHERE user_id = ? AND LOWER(course_name) = LOWER(?)";
        List<Object> params = new ArrayList<>(List.of(userId, s(courseName)));
        if (excludeId != null && excludeId != 0) {
            sql += " AND course_id != ?";
            params.add(excludeId);
        }
        return fetchOne(sql, params.toArray()) != null;
    }

    public long addCourse(long userId, String courseName, String platform, String instructor,
                          String startDate, String endDate, String status, int progressPercent,
                          String priority, String courseLink, String notes) {
        return insert(
            "INSERT INTO courses (user_id, course_name, platform, instructor, start_date, end_date, "
                + "status, progress_percent, priority, course_link, notes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            userId, s(courseName), s(platform), s(instructor), startDate == null ? "" : startDate,
            endDate == null ? "" : endDate, status, progressPercent, priority, s(courseLink), s(notes));
    }

    private void attachModuleCounts(long userId, Map<String, Object> course) {
        Map<String, Object> m = fetchOne(
            "SELECT COUNT(*) AS total_modules, "
                + "SUM(CASE WHEN is_completed = 1 THEN 1 ELSE 0 END) AS completed_modules "
                + "FROM modules WHERE user_id = ? AND course_id = ?",
            userId, course.get("course_id"));
        course.put("total_modules", m == null ? 0 : toInt(m.get("total_modules")));
        course.put("completed_modules", m == null ? 0 : toInt(m.get("completed_modules")));
    }

    public List<Map<String, Object>> getCourses(long userId, String search, String statusFilter,
                                                String platformFilter) {
        StringBuilder sql = new StringBuilder("SELECT * FROM courses WHERE user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (search != null && !search.isBlank()) {
            sql.append(" AND (course_name LIKE ? OR instructor LIKE ?)");
            params.add("%" + search + "%");
            params.add("%" + search + "%");
        }
        if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equals("All")) {
            sql.append(" AND status = ?");
            params.add(statusFilter);
        }
        if (platformFilter != null && !platformFilter.isBlank() && !platformFilter.equals("All")) {
            sql.append(" AND platform = ?");
            params.add(platformFilter);
        }
        sql.append(" ORDER BY course_id DESC");

        List<Map<String, Object>> courses = fetchAll(sql.toString(), params.toArray());
        for (Map<String, Object> c : courses) {
            attachModuleCounts(userId, c);
        }
        return courses;
    }

    public Map<String, Object> getCourseById(long userId, long courseId) {
        Map<String, Object> course = fetchOne(
            "SELECT * FROM courses WHERE user_id = ? AND course_id = ?", userId, courseId);
        if (course != null) {
            attachModuleCounts(userId, course);
        }
        return course;
    }

    public int updateCourse(long userId, long courseId, String courseName, String platform,
                            String instructor, String startDate, String endDate, String status,
                            String priority, String courseLink, String notes) {
        int res = execute(
            "UPDATE courses SET course_name = ?, platform = ?, instructor = ?, start_date = ?, "
                + "end_date = ?, status = ?, priority = ?, course_link = ?, notes = ? "
                + "WHERE user_id = ? AND course_id = ?",
            s(courseName), s(platform), s(instructor), startDate, endDate, status, priority,
            s(courseLink), s(notes), userId, courseId);
        recalculateCourseProgress(userId, courseId);
        return res;
    }

    /** Deletes a course; modules, logs, deadlines and exams are removed by ON DELETE CASCADE. */
    public int deleteCourse(long userId, long courseId) {
        return execute("DELETE FROM courses WHERE user_id = ? AND course_id = ?", userId, courseId);
    }

    /**
     * progress = completed_modules / total_modules * 100.
     * 0% -> Not Started, 1-99% -> In Progress, 100% -> Completed. "On Hold" is a manual override and is kept.
     */
    public Map<String, Object> recalculateCourseProgress(long userId, long courseId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> course = fetchOne(
            "SELECT status FROM courses WHERE user_id = ? AND course_id = ?", userId, courseId);
        if (course == null) {
            result.put("progress_percent", 0);
            result.put("status", "Not Started");
            return result;
        }

        String currentStatus = String.valueOf(course.get("status"));
        Map<String, Object> stats = fetchOne(
            "SELECT COUNT(*) AS total, SUM(CASE WHEN is_completed = 1 THEN 1 ELSE 0 END) AS completed "
                + "FROM modules WHERE user_id = ? AND course_id = ?",
            userId, courseId);
        int total = stats == null ? 0 : toInt(stats.get("total"));
        int completed = stats == null ? 0 : toInt(stats.get("completed"));

        int pct = total > 0 ? (int) Math.round(completed * 100.0 / total) : 0;

        String newStatus;
        if ("On Hold".equals(currentStatus)) {
            newStatus = "On Hold";
        } else if (pct == 0) {
            newStatus = "Not Started";
        } else if (pct == 100) {
            newStatus = "Completed";
        } else {
            newStatus = "In Progress";
        }

        execute("UPDATE courses SET progress_percent = ?, status = ? WHERE user_id = ? AND course_id = ?",
            pct, newStatus, userId, courseId);

        result.put("progress_percent", pct);
        result.put("status", newStatus);
        result.put("total_modules", total);
        result.put("completed_modules", completed);
        return result;
    }

    // ==================================================================
    // 3. Modules, notes & references
    // ==================================================================

    /** position may be null (auto-assigned as last + 1). referenceLinks is a JSON string like "[]". */
    public long addModule(long userId, long courseId, String title, Integer position, String notes,
                          String referenceLinks, int isImportant, int isWeak, String keyPoints) {
        if (position == null) {
            Map<String, Object> max = fetchOne(
                "SELECT COALESCE(MAX(position), 0) AS max_p FROM modules WHERE user_id = ? AND course_id = ?",
                userId, courseId);
            position = (max == null ? 0 : toInt(max.get("max_p"))) + 1;
        }
        long id = insert(
            "INSERT INTO modules (user_id, course_id, title, position, is_completed, completed_date, "
                + "notes, reference_links, is_important, is_weak, key_points) "
                + "VALUES (?, ?, ?, ?, 0, '', ?, ?, ?, ?, ?)",
            userId, courseId, s(title), position, s(notes),
            (referenceLinks == null || referenceLinks.isBlank()) ? "[]" : referenceLinks,
            isImportant, isWeak, s(keyPoints));
        recalculateCourseProgress(userId, courseId);
        return id;
    }

    public List<Map<String, Object>> getCourseModules(long userId, long courseId) {
        List<Map<String, Object>> modules = fetchAll(
            "SELECT * FROM modules WHERE user_id = ? AND course_id = ? ORDER BY position ASC, module_id ASC",
            userId, courseId);
        for (Map<String, Object> m : modules) {
            m.put("reference_links", parseLinks(m.get("reference_links")));
        }
        return modules;
    }

    public Map<String, Object> getModuleById(long userId, long moduleId) {
        Map<String, Object> m = fetchOne(
            "SELECT * FROM modules WHERE user_id = ? AND module_id = ?", userId, moduleId);
        if (m != null) {
            m.put("reference_links", parseLinks(m.get("reference_links")));
        }
        return m;
    }

    public int updateModule(long userId, long moduleId, String title, String notes,
                            String referenceLinks, int isImportant, int isWeak, String keyPoints) {
        return execute(
            "UPDATE modules SET title = ?, notes = ?, reference_links = ?, is_important = ?, "
                + "is_weak = ?, key_points = ? WHERE user_id = ? AND module_id = ?",
            s(title), s(notes), (referenceLinks == null || referenceLinks.isBlank()) ? "[]" : referenceLinks,
            isImportant, isWeak, s(keyPoints), userId, moduleId);
    }

    /** Marks a module done / undone and recalculates the course progress. */
    public Map<String, Object> toggleModuleCompletion(long userId, long moduleId, int isCompleted) {
        String completedDate = isCompleted == 1 ? LocalDate.now().toString() : "";
        execute("UPDATE modules SET is_completed = ?, completed_date = ? WHERE user_id = ? AND module_id = ?",
            isCompleted, completedDate, userId, moduleId);

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> mod = getModuleById(userId, moduleId);
        if (mod != null) {
            result.put("module", mod);
            result.put("course_progress", recalculateCourseProgress(userId, toLong(mod.get("course_id"))));
        } else {
            result.put("module", null);
            result.put("course_progress", null);
        }
        return result;
    }

    public int deleteModule(long userId, long moduleId) {
        Map<String, Object> mod = getModuleById(userId, moduleId);
        if (mod == null) {
            return 0;
        }
        long cid = toLong(mod.get("course_id"));
        int res = execute("DELETE FROM modules WHERE user_id = ? AND module_id = ?", userId, moduleId);
        recalculateCourseProgress(userId, cid);
        return res;
    }

    // ==================================================================
    // 4. Study log
    // ==================================================================

    public boolean studyLogExists(long userId, long courseId, String studyDate, String topicCovered) {
        return fetchOne(
            "SELECT log_id FROM study_log WHERE user_id = ? AND course_id = ? AND study_date = ? "
                + "AND LOWER(topic_covered) = LOWER(?)",
            userId, courseId, studyDate, s(topicCovered)) != null;
    }

    public long addStudyLog(long userId, long courseId, String studyDate, double hoursSpent, String topicCovered) {
        return insert(
            "INSERT INTO study_log (user_id, course_id, study_date, hours_spent, topic_covered) VALUES (?, ?, ?, ?, ?)",
            userId, courseId, studyDate, hoursSpent, s(topicCovered));
    }

    /** courseId may be null for all courses. */
    public List<Map<String, Object>> getStudyLogs(long userId, Long courseId) {
        String sql = "SELECT s.log_id, s.course_id, c.course_name, c.platform, s.study_date, s.hours_spent, s.topic_covered "
            + "FROM study_log s JOIN courses c ON s.course_id = c.course_id WHERE s.user_id = ?";
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (courseId != null && courseId != 0) {
            sql += " AND s.course_id = ?";
            params.add(courseId);
        }
        sql += " ORDER BY s.study_date DESC, s.log_id DESC";
        return fetchAll(sql, params.toArray());
    }

    public int deleteStudyLog(long userId, long logId) {
        return execute("DELETE FROM study_log WHERE user_id = ? AND log_id = ?", userId, logId);
    }

    // ==================================================================
    // 5. Deadlines
    // ==================================================================

    public boolean deadlineExists(long userId, long courseId, String taskName, String dueDate) {
        return fetchOne(
            "SELECT deadline_id FROM deadlines WHERE user_id = ? AND course_id = ? "
                + "AND LOWER(task_name) = LOWER(?) AND due_date = ?",
            userId, courseId, s(taskName), dueDate) != null;
    }

    public long addDeadline(long userId, long courseId, String taskName, String dueDate, int isDone) {
        return insert(
            "INSERT INTO deadlines (user_id, course_id, task_name, due_date, is_done) VALUES (?, ?, ?, ?, ?)",
            userId, courseId, s(taskName), dueDate, isDone);
    }

    public List<Map<String, Object>> getDeadlines(long userId, Long courseId, boolean includeCompleted) {
        String sql = "SELECT d.deadline_id, d.course_id, c.course_name, c.platform, d.task_name, d.due_date, d.is_done "
            + "FROM deadlines d JOIN courses c ON d.course_id = c.course_id WHERE d.user_id = ?";
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (courseId != null && courseId != 0) {
            sql += " AND d.course_id = ?";
            params.add(courseId);
        }
        if (!includeCompleted) {
            sql += " AND d.is_done = 0";
        }
        sql += " ORDER BY d.is_done ASC, d.due_date ASC";
        return fetchAll(sql, params.toArray());
    }

    public int toggleDeadline(long userId, long deadlineId, int isDone) {
        return execute("UPDATE deadlines SET is_done = ? WHERE user_id = ? AND deadline_id = ?",
            isDone, userId, deadlineId);
    }

    public int deleteDeadline(long userId, long deadlineId) {
        return execute("DELETE FROM deadlines WHERE user_id = ? AND deadline_id = ?", userId, deadlineId);
    }

    // ==================================================================
    // 6. Certificates
    // ==================================================================

    public long addCertificate(long userId, long courseId, String certificateLink, String issueDate, String grade) {
        return insert(
            "INSERT INTO certificates (user_id, course_id, certificate_link, issue_date, grade) VALUES (?, ?, ?, ?, ?)",
            userId, courseId, s(certificateLink), issueDate == null ? "" : issueDate, s(grade));
    }

    public List<Map<String, Object>> getCertificates(long userId) {
        return fetchAll(
            "SELECT cert.cert_id, cert.course_id, c.course_name, c.platform, cert.certificate_link, "
                + "cert.issue_date, cert.grade FROM certificates cert "
                + "JOIN courses c ON cert.course_id = c.course_id "
                + "WHERE cert.user_id = ? ORDER BY cert.cert_id DESC",
            userId);
    }

    public int deleteCertificate(long userId, long certId) {
        return execute("DELETE FROM certificates WHERE user_id = ? AND cert_id = ?", userId, certId);
    }

    // ==================================================================
    // 7. Exams & priority revision lists
    // ==================================================================

    public long addExam(long userId, long courseId, String examName, String examDate, String examTime,
                        String syllabusNotes, String coveredModuleIds) {
        return insert(
            "INSERT INTO exams (user_id, course_id, exam_name, exam_date, exam_time, syllabus_notes, covered_module_ids) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
            userId, courseId, s(examName), examDate, s(examTime), s(syllabusNotes),
            (coveredModuleIds == null || coveredModuleIds.isBlank()) ? "[]" : coveredModuleIds);
    }

    public List<Map<String, Object>> getExams(long userId) {
        List<Map<String, Object>> exams = fetchAll(
            "SELECT e.exam_id, e.course_id, c.course_name, c.platform, e.exam_name, e.exam_date, e.exam_time, "
                + "e.syllabus_notes, e.covered_module_ids FROM exams e "
                + "JOIN courses c ON e.course_id = c.course_id WHERE e.user_id = ? ORDER BY e.exam_date ASC",
            userId);
        LocalDate today = LocalDate.now();

        for (Map<String, Object> ex : exams) {
            try {
                String raw = String.valueOf(ex.get("exam_date"));
                LocalDate examDate = LocalDate.parse(raw.substring(0, 10));
                long delta = ChronoUnit.DAYS.between(today, examDate);
                ex.put("days_left", delta);
                if (delta < 0) {
                    ex.put("countdown_text", "Exam passed");
                    ex.put("urgency_color", "gray");
                } else if (delta == 0) {
                    ex.put("countdown_text", "🚨 Today!");
                    ex.put("urgency_color", "red");
                } else if (delta == 1) {
                    ex.put("countdown_text", "🔥 Tomorrow");
                    ex.put("urgency_color", "red");
                } else if (delta <= 3) {
                    ex.put("countdown_text", "⏰ In " + delta + " days");
                    ex.put("urgency_color", "red");
                } else if (delta <= 7) {
                    ex.put("countdown_text", "In " + delta + " days");
                    ex.put("urgency_color", "amber");
                } else {
                    ex.put("countdown_text", "In " + delta + " days");
                    ex.put("urgency_color", "green");
                }
            } catch (Exception e) {
                ex.put("days_left", 999);
                ex.put("countdown_text", "Scheduled");
                ex.put("urgency_color", "blue");
            }

            Map<String, Object> rev = fetchOne(
                "SELECT COUNT(*) AS total, SUM(CASE WHEN is_revised = 1 THEN 1 ELSE 0 END) AS done "
                    + "FROM revision_items WHERE user_id = ? AND exam_id = ?",
                userId, ex.get("exam_id"));
            ex.put("revision_total", rev == null ? 0 : toInt(rev.get("total")));
            ex.put("revision_done", rev == null ? 0 : toInt(rev.get("done")));
        }
        return exams;
    }

    public Map<String, Object> getExamById(long userId, long examId) {
        Map<String, Object> exam = fetchOne(
            "SELECT e.*, c.course_name, c.platform FROM exams e "
                + "JOIN courses c ON e.course_id = c.course_id WHERE e.user_id = ? AND e.exam_id = ?",
            userId, examId);
        if (exam != null) {
            exam.put("covered_module_ids", parseIdList(exam.get("covered_module_ids")));
        }
        return exam;
    }

    public int updateExam(long userId, long examId, long courseId, String examName, String examDate,
                          String examTime, String syllabusNotes, String coveredModuleIds) {
        return execute(
            "UPDATE exams SET course_id = ?, exam_name = ?, exam_date = ?, exam_time = ?, "
                + "syllabus_notes = ?, covered_module_ids = ? WHERE user_id = ? AND exam_id = ?",
            courseId, s(examName), examDate, s(examTime), s(syllabusNotes),
            (coveredModuleIds == null || coveredModuleIds.isBlank()) ? "[]" : coveredModuleIds,
            userId, examId);
    }

    public int deleteExam(long userId, long examId) {
        return execute("DELETE FROM exams WHERE user_id = ? AND exam_id = ?", userId, examId);
    }

    /**
     * Automated "what to revise" list, ordered by priority:
     * 1 = weak topics, 2 = important topics, 3 = incomplete modules, 4 = everything else.
     */
    public Map<String, Object> getExamRevisionChecklist(long userId, long examId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> exam = getExamById(userId, examId);
        if (exam == null) {
            result.put("exam", null);
            result.put("items", new ArrayList<>());
            result.put("total_items", 0);
            result.put("revised_items", 0);
            result.put("progress_percent", 0);
            return result;
        }

        long cid = toLong(exam.get("course_id"));
        List<Map<String, Object>> allModules = getCourseModules(userId, cid);

        @SuppressWarnings("unchecked")
        List<Long> coveredIds = (List<Long>) exam.get("covered_module_ids");
        List<Map<String, Object>> targetModules = new ArrayList<>();
        for (Map<String, Object> m : allModules) {
            if (coveredIds == null || coveredIds.isEmpty() || coveredIds.contains(toLong(m.get("module_id")))) {
                targetModules.add(m);
            }
        }

        // Make sure a revision_items row exists for each module
        for (Map<String, Object> m : targetModules) {
            execute("INSERT OR IGNORE INTO revision_items (user_id, exam_id, module_id, is_revised) VALUES (?, ?, ?, 0)",
                userId, examId, m.get("module_id"));
        }

        Map<Long, Integer> revMap = new HashMap<>();
        for (Map<String, Object> r : fetchAll(
            "SELECT module_id, is_revised FROM revision_items WHERE user_id = ? AND exam_id = ?", userId, examId)) {
            revMap.put(toLong(r.get("module_id")), toInt(r.get("is_revised")));
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> m : targetModules) {
            long mid = toLong(m.get("module_id"));
            int rank;
            String reason;
            if (toInt(m.get("is_weak")) == 1) {
                rank = 1;
                reason = "⚠️ Weak Topic";
            } else if (toInt(m.get("is_important")) == 1) {
                rank = 2;
                reason = "⭐ Important Concept";
            } else if (toInt(m.get("is_completed")) != 1) {
                rank = 3;
                reason = "⏳ Incomplete Module";
            } else {
                rank = 4;
                reason = "Completed";
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("module_id", mid);
            item.put("title", m.get("title"));
            item.put("is_completed", toInt(m.get("is_completed")));
            item.put("is_important", toInt(m.get("is_important")));
            item.put("is_weak", toInt(m.get("is_weak")));
            item.put("key_points", m.get("key_points"));
            item.put("notes", m.get("notes"));
            item.put("priority_rank", rank);
            item.put("reason", reason);
            item.put("is_revised", revMap.getOrDefault(mid, 0));
            items.add(item);
        }

        items.sort(Comparator
            .comparingInt((Map<String, Object> it) -> toInt(it.get("priority_rank")))
            .thenComparing(it -> String.valueOf(it.get("title"))));

        int total = items.size();
        int done = 0;
        for (Map<String, Object> it : items) {
            if (toInt(it.get("is_revised")) == 1) {
                done++;
            }
        }
        int pct = total > 0 ? (int) Math.round(done * 100.0 / total) : 0;

        result.put("exam", exam);
        result.put("items", items);
        result.put("total_items", total);
        result.put("revised_items", done);
        result.put("progress_percent", pct);
        return result;
    }

    public int toggleRevisionItem(long userId, long examId, long moduleId, int isRevised) {
        String revisedAt = isRevised == 1
            ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            : "";
        return execute(
            "INSERT INTO revision_items (user_id, exam_id, module_id, is_revised, revised_at) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON CONFLICT(exam_id, module_id) DO UPDATE SET is_revised = ?, revised_at = ?",
            userId, examId, moduleId, isRevised, revisedAt, isRevised, revisedAt);
    }
}
