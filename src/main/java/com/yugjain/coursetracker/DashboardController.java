package com.yugjain.coursetracker;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** /api/kpi and /api/analytics/charts (data for Chart.js). */
@RestController
public class DashboardController {

    private final AnalyticsService analytics;
    private final String engineName;

    public DashboardController(AnalyticsService analytics, @Value("${spring.datasource.url}") String dbUrl) {
        this.analytics = analytics;
        this.engineName = "SQLite (" + dbUrl.replace("jdbc:sqlite:", "") + ")";
    }

    @GetMapping("/api/kpi")
    public Map<String, Object> kpis(HttpSession session) {
        Map<String, Object> kpi = analytics.getKpiSummary(SessionUser.id(session));
        kpi.put("engine_name", engineName);
        return kpi;
    }

    private static Map<String, Object> series(List<?> labels, List<?> values) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("labels", labels);
        m.put("values", values);
        return m;
    }

    @GetMapping("/api/analytics/charts")
    public Map<String, Object> charts(HttpSession session) {
        long userId = SessionUser.id(session);

        List<Map.Entry<String, Double>> hours = new ArrayList<>(analytics.getHoursPerCourse(userId).entrySet());
        Collections.reverse(hours);
        List<String> hoursLabels = new ArrayList<>();
        List<Double> hoursValues = new ArrayList<>();
        for (Map.Entry<String, Double> e : hours) {
            hoursLabels.add(e.getKey());
            hoursValues.add(e.getValue());
        }

        Map<String, Integer> status = analytics.getStatusCounts(userId);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH);
        List<String> trendLabels = new ArrayList<>();
        List<Double> trendValues = new ArrayList<>();
        for (Map.Entry<String, Double> e : analytics.getDailyHours(userId).entrySet()) {
            trendLabels.add(LocalDate.parse(e.getKey()).format(fmt));
            trendValues.add(e.getValue());
        }

        Map<String, Integer> platforms = analytics.getPlatformCounts(userId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hours_per_course", series(hoursLabels, hoursValues));
        body.put("status_distribution", series(new ArrayList<>(status.keySet()), new ArrayList<>(status.values())));
        body.put("study_trend", series(trendLabels, trendValues));
        body.put("platform_breakdown", series(new ArrayList<>(platforms.keySet()), new ArrayList<>(platforms.values())));
        return body;
    }
}
