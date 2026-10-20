package com.yugjain.coursetracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/test_course_tracker.db")
class DatabaseTest {

    @Autowired
    Database db;

    @Test
    void progressFollowsModules() {
        long uid = db.createUser("t" + System.nanoTime(), "hash", "Test", "CS", "", "", "#4F46E5");
        long cid = db.addCourse(uid, "Java", "Udemy", "", "", "", "Not Started", 0, "Medium", "", "");
        long m1 = db.addModule(uid, cid, "Intro", null, "", "[]", 0, 0, "");
        db.addModule(uid, cid, "Loops", null, "", "[]", 0, 0, "");

        db.toggleModuleCompletion(uid, m1, 1);
        Map<String, Object> c = db.getCourseById(uid, cid);

        assertEquals(50, ((Number) c.get("progress_percent")).intValue());
        assertEquals("In Progress", c.get("status"));
    }

    @Test
    void revisionChecklistPutsWeakTopicsFirst() {
        long uid = db.createUser("r" + System.nanoTime(), "hash", "Test", "CS", "", "", "#4F46E5");
        long cid = db.addCourse(uid, "DBMS", "NPTEL", "", "", "", "Not Started", 0, "High", "", "");
        db.addModule(uid, cid, "Normal topic", null, "", "[]", 0, 0, "");
        db.addModule(uid, cid, "Weak topic", null, "", "[]", 0, 1, "");
        long eid = db.addExam(uid, cid, "Midterm", "2030-01-01", "10:00", "", "[]");

        Map<String, Object> checklist = db.getExamRevisionChecklist(uid, eid);
        @SuppressWarnings("unchecked")
        var items = (java.util.List<Map<String, Object>>) checklist.get("items");

        assertEquals(2, items.size());
        assertEquals("Weak topic", items.get(0).get("title"));
    }
}
