CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    name TEXT NOT NULL,
    subject TEXT NOT NULL,
    roll_no TEXT DEFAULT '',
    email TEXT DEFAULT '',
    avatar_color TEXT DEFAULT '#4F46E5',
    created_at TEXT DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS courses (
    course_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    course_name TEXT NOT NULL,
    platform TEXT NOT NULL,
    instructor TEXT DEFAULT '',
    start_date TEXT DEFAULT '',
    end_date TEXT DEFAULT '',
    status TEXT DEFAULT 'Not Started',
    progress_percent INTEGER DEFAULT 0,
    priority TEXT DEFAULT 'Medium',
    course_link TEXT DEFAULT '',
    notes TEXT DEFAULT '',
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS modules (
    module_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    position INTEGER DEFAULT 1,
    is_completed INTEGER DEFAULT 0,
    completed_date TEXT DEFAULT '',
    notes TEXT DEFAULT '',
    reference_links TEXT DEFAULT '[]',
    is_important INTEGER DEFAULT 0,
    is_weak INTEGER DEFAULT 0,
    key_points TEXT DEFAULT '',
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES courses (course_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS study_log (
    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,
    study_date TEXT NOT NULL,
    hours_spent REAL NOT NULL,
    topic_covered TEXT DEFAULT '',
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES courses (course_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS deadlines (
    deadline_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,
    task_name TEXT NOT NULL,
    due_date TEXT NOT NULL,
    is_done INTEGER DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES courses (course_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS certificates (
    cert_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,
    certificate_link TEXT NOT NULL,
    issue_date TEXT DEFAULT '',
    grade TEXT DEFAULT '',
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES courses (course_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS exams (
    exam_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,
    exam_name TEXT NOT NULL,
    exam_date TEXT NOT NULL,
    exam_time TEXT DEFAULT '',
    syllabus_notes TEXT DEFAULT '',
    covered_module_ids TEXT DEFAULT '[]',
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES courses (course_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS revision_items (
    revision_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    exam_id INTEGER NOT NULL,
    module_id INTEGER NOT NULL,
    is_revised INTEGER DEFAULT 0,
    revised_at TEXT DEFAULT '',
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    FOREIGN KEY (exam_id) REFERENCES exams (exam_id) ON DELETE CASCADE,
    FOREIGN KEY (module_id) REFERENCES modules (module_id) ON DELETE CASCADE,
    UNIQUE(exam_id, module_id)
);
