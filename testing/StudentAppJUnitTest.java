import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class StudentAppJUnitTest {
 @Test void attendance75percent(){assertEquals(75,StudentApp.percent(15,20));}
 @Test void attendanceZero(){assertEquals(0,StudentApp.percent(0,20));}
 @Test void attendanceHundred(){assertEquals(100,StudentApp.percent(20,20));}
 @Test void rejectZeroClasses(){assertThrows(IllegalArgumentException.class,()->StudentApp.percent(0,0));}
 @Test void rejectPresentExceedingTotal(){assertThrows(IllegalArgumentException.class,()->StudentApp.percent(21,20));}
 @Test void rejectNegativeAttendance(){assertThrows(IllegalArgumentException.class,()->StudentApp.percent(-1,20));}
 @Test void marksBoundary(){assertTrue(StudentApp.validMark(0));assertTrue(StudentApp.validMark(100));}
 @Test void marksOutsideRange(){assertFalse(StudentApp.validMark(-1));assertFalse(StudentApp.validMark(101));}
 @Test void validRegistrationId(){assertTrue(StudentApp.validId("24MIS0132"));assertFalse(StudentApp.validId("X"));}
}