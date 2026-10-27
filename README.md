# Student Management System — ISWE305P

Java 21 web application for an academic Software Testing project.

## Modules
Login, Student Registration, Student Management, Course Management, Enrollment, Attendance, Marks and Reports.

## Run
```bash
mvn test
mvn compile
java -cp target/classes StudentApp
```
Open http://127.0.0.1:8188

Default local demonstration login: admin / Demo123! (set STUDENT_ADMIN_USER and STUDENT_ADMIN_PASS to override).

## Layout
- `src/main/java/Frontend.java` — frontend: HTML, CSS, forms, subject selectors, and tables
- `src/main/java/StudentApp.java` — backend: HTTP server, authentication, validation, records, and file storage
- `testing/` — JUnit 5 tests
- `pom.xml` — Maven build configuration
- `.github/workflows/java-tests.yml` — automated JUnit checks

This is a local educational demonstration, not production-grade identity or student-data software.
