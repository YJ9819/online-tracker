package com.yugjain.coursetracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/test_course_tracker.db")
class AnalyticsServiceTest {

    @Autowired
    Database db;

    @Autowired
    AnalyticsService analytics;

    @Test
    void kpiAndChartDataAreCalculatedPerUser() {
        long uid = db.createUser("a" + System.nanoTime(), "hash", "Test", "CS", "", "", "#4F46E5");
        long c1 = db.addCourse(uid, "Java", "Udemy", "", "", "", "Not Started", 0, "Medium", "", "");
        db.addCourse(uid, "Python", "Udemy", "", "", "", "Not Started", 0, "Medium", "", "");
        db.addCourse(uid, "DBMS", "NPTEL", "", "", "", "Not Started", 0, "Medium", "", "");

        db.addStudyLog(uid, c1, "2026-10-20", 1.5, "Intro");
        db.addStudyLog(uid, c1, "2026-10-20", 1.5, "Loops");
        db.addDeadline(uid, c1, "Assignment 1", "2026-11-01", 0);
        db.addExam(uid, c1, "Final", LocalDate.now().plusDays(5).toString(), "10:00", "", "[]");

        Map<String, Object> kpi = analytics.getKpiSummary(uid);
        assertEquals(3, kpi.get("total_courses"));
        assertEquals(3.0, (double) kpi.get("total_hours"), 0.001);
        assertEquals(2, kpi.get("total_sessions"));
        assertEquals(1L, kpi.get("pending_deadlines"));
        assertEquals(1L, kpi.get("upcoming_exams"));
        assertEquals("Udemy", kpi.get("most_used_platform"));

        assertEquals(3.0, analytics.getDailyHours(uid).get("2026-10-20"), 0.001);
        assertEquals(2, analytics.getPlatformCounts(uid).get("Udemy"));
    }

    @Test
    void newUserHasEmptyAnalytics() {
        long uid = db.createUser("e" + System.nanoTime(), "hash", "Empty", "CS", "", "", "#4F46E5");
        Map<String, Object> kpi = analytics.getKpiSummary(uid);
        assertEquals(0, kpi.get("total_courses"));
        assertEquals("N/A", kpi.get("most_used_platform"));
        assertTrue(analytics.getDailyHours(uid).isEmpty());
    }
}
