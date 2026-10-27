import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudentWorkflowJUnitTest {
    @Test void rejectsBadStudentNames() {
        assertFalse(StudentApp.validName(""));
        assertFalse(StudentApp.validName("   "));
        assertFalse(StudentApp.validName("x".repeat(101)));
        assertTrue(StudentApp.validName("Test Student"));
    }
    @Test void validatesIdentifierBoundaries() {
        assertFalse(StudentApp.validId("AB"));
        assertTrue(StudentApp.validId("ABC"));
        assertTrue(StudentApp.validId("A".repeat(20)));
        assertFalse(StudentApp.validId("A".repeat(21)));
        assertFalse(StudentApp.validId("ABC!"));
    }
    @Test void reportsPendingFailPass() {
        assertEquals("Pending",StudentApp.result(null));
        assertEquals("Fail",StudentApp.result(39));
        assertEquals("Pass",StudentApp.result(40));
    }
    @Test void parsesFormEncoding() {
        assertEquals("Two Words",StudentApp.params("name=Two+Words").get("name"));
    }
    @Test void enrollmentKeyPreservesPair() {
        assertEquals("STU001|CSE101",StudentApp.key("STU001","CSE101"));
    }
    @Test void attendanceBounds() {
        assertEquals(75,StudentApp.percent(15,20));
        assertThrows(IllegalArgumentException.class,()->StudentApp.percent(21,20));
    }
}