package com.yugjain.coursetracker;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Java port of analytics.py (per-user analytics).
 * Pandas aggregations are done with plain Java streams.
 * Chart data is returned as maps; the web page draws the charts with Chart.js.
 */
@Service
public class AnalyticsService {

    private final Database db;

    public AnalyticsService(Database db) {
        this.db = db;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** Counts values of a column, ordered by count (high to low), then name (like pandas value_counts). */
    private static Map<String, Integer> countBy(List<Map<String, Object>> rows, String key) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Map<String, Object> r : rows) {
            Object v = r.get(key);
            if (v != null) {
                counts.merge(String.valueOf(v), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    // ------------------------------------------------------------------
    // KPI summary
    // ------------------------------------------------------------------

    public Map<String, Object> getKpiSummary(long userId) {
        List<Map<String, Object>> courses = db.getCourses(userId, "", "", "");
        List<Map<String, Object>> logs = db.getStudyLogs(userId, null);
        List<Map<String, Object>> deadlines = db.getDeadlines(userId, null, true);
        List<Map<String, Object>> exams = db.getExams(userId);

        int total = courses.size();
        long completed = courses.stream().filter(c -> "Completed".equals(c.get("status"))).count();
        long inProgress = courses.stream().filter(c -> "In Progress".equals(c.get("status"))).count();
        double avgProgress = courses.isEmpty() ? 0.0
            : courses.stream().mapToDouble(c -> num(c.get("progress_percent"))).average().orElse(0.0);

        double totalHours = logs.stream().mapToDouble(l -> num(l.get("hours_spent"))).sum();
        long pending = deadlines.stream().filter(d -> num(d.get("is_done")) == 0).count();
        long upcoming = exams.stream().filter(e -> num(e.get("days_left")) >= 0).count();

        Map<String, Integer> platforms = countBy(courses, "platform");
        String mostUsed = platforms.isEmpty() ? "N/A" : platforms.keySet().iterator().next();

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("total_courses", total);
        kpi.put("completed_courses", completed);
        kpi.put("in_progress_courses", inProgress);
        kpi.put("avg_progress", round1(avgProgress));
        kpi.put("total_hours", round1(totalHours));
        kpi.put("total_sessions", logs.size());
        kpi.put("pending_deadlines", pending);
        kpi.put("upcoming_exams", upcoming);
        kpi.put("most_used_platform", mostUsed);
        return kpi;
    }

    /** Course count and average progress for each platform (sorted by platform name). */
    public List<Map<String, Object>> getPlatformBreakdown(long userId) {
        List<Map<String, Object>> courses = db.getCourses(userId, "", "", "");
        Map<String, List<Map<String, Object>>> grouped = new TreeMap<>();
        for (Map<String, Object> c : courses) {
            grouped.computeIfAbsent(String.valueOf(c.get("platform")), k -> new ArrayList<>()).add(c);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
            double avg = e.getValue().stream().mapToDouble(c -> num(c.get("progress_percent"))).average().orElse(0.0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("platform", e.getKey());
            row.put("course_count", e.getValue().size());
            row.put("avg_progress", round1(avg));
            result.add(row);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Chart data (replaces the Matplotlib plot_* methods)
    // ------------------------------------------------------------------

    /** Total study hours per course, smallest first (bar chart). */
    public Map<String, Double> getHoursPerCourse(long userId) {
        Map<String, Double> sums = new HashMap<>();
        for (Map<String, Object> l : db.getStudyLogs(userId, null)) {
            sums.merge(String.valueOf(l.get("course_name")), num(l.get("hours_spent")), Double::sum);
        }
        return sums.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, e -> round1(e.getValue()), (a, b) -> a, LinkedHashMap::new));
    }

    /** Number of courses per status (donut chart). */
    public Map<String, Integer> getStatusCounts(long userId) {
        return countBy(db.getCourses(userId, "", "", ""), "status");
    }

    /** Number of courses per platform (bar chart). */
    public Map<String, Integer> getPlatformCounts(long userId) {
        return countBy(db.getCourses(userId, "", "", ""), "platform");
    }

    /** Hours studied per day, in date order (line chart). Rows with invalid dates are skipped. */
    public Map<String, Double> getDailyHours(long userId) {
        Map<LocalDate, Double> daily = new TreeMap<>();
        for (Map<String, Object> l : db.getStudyLogs(userId, null)) {
            try {
                LocalDate d = LocalDate.parse(String.valueOf(l.get("study_date")).substring(0, 10));
                daily.merge(d, num(l.get("hours_spent")), Double::sum);
            } catch (Exception ignored) {
                // skip invalid date
            }
        }
        Map<String, Double> result = new LinkedHashMap<>();
        daily.forEach((d, h) -> result.put(d.toString(), round1(h)));
        return result;
    }

    /** Everything the dashboard needs in one call (handy for a JSON endpoint). */
    public Map<String, Object> getDashboardData(long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kpi", getKpiSummary(userId));
        data.put("platform_breakdown", getPlatformBreakdown(userId));
        data.put("hours_per_course", getHoursPerCourse(userId));
        data.put("status_counts", getStatusCounts(userId));
        data.put("platform_counts", getPlatformCounts(userId));
        data.put("daily_hours", getDailyHours(userId));
        return data;
    }
}
