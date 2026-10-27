import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Presentation tests use supplied data and never modify application storage. */
class FrontendJUnitTest {
    private String render(String path, String subject) {
        List<String[]> enrolled = Collections.singletonList(new String[]{"24MIS0002", "Asha <script>"});
        List<String[]> report = Collections.singletonList(new String[]{"24MIS0002", "Asha <script>", "30/40 (75%)", "80", "Pass"});
        return Frontend.render(path, "", "token123", subject,
            Map.of("24MIS0002", "Asha <script>"), Map.of("CSE101", "Testing & Quality"), enrolled, report);
    }
    @Test void escapesAllHtmlAttributeCharacters() {
        assertEquals("&lt;&gt;&amp;&quot;&#39;", Frontend.esc("<>&\"'"));
    }
    @Test void rendersEscapedPageTitleAndNavigation() {
        String page=Frontend.page("<Title>", "Content");
        assertTrue(page.contains("<title>&lt;Title&gt;</title>"));
        assertTrue(page.contains("href='/reports'"));
        assertTrue(page.contains("<style>"));
    }
    @Test void loginUsesPostAndMaskedPassword() {
        assertTrue(Frontend.login().contains("method='post'"));
        assertTrue(Frontend.login().contains("type='password'"));
    }
    @Test void selectedSubjectIsRetainedAndEscaped() {
        String page=render("/enrollment", "CSE101");
        assertTrue(page.contains("value='CSE101' selected"));
        assertTrue(page.contains("Testing &amp; Quality"));
        assertTrue(page.contains("Asha &lt;script&gt;"));
        assertFalse(page.contains("Asha <script>"));
    }
    @Test void academicPagesRequireSubjectSelection() {
        for(String path:List.of("/enrollment","/attendance","/marks","/reports")) {
            String page=render(path, "");
            assertTrue(page.contains("Select a subject"), path);
            assertFalse(page.contains("<td>24MIS0002</td>"), path);
        }
    }
    @Test void attendanceAndMarksFormsCarrySubjectAndCsrf() {
        for(String path:List.of("/attendance","/marks")) {
            String page=render(path,"CSE101");
            assertTrue(page.contains("name='code' value='CSE101'"));
            assertTrue(page.contains("name='csrf' value='token123'"));
        }
    }
    @Test void reportDisplaysAttendanceMarkAndResult() {
        String page=render("/reports","CSE101");
        assertTrue(page.contains("<td>30/40 (75%)</td>"));
        assertTrue(page.contains("<td>80</td><td>Pass</td>"));
        assertFalse(page.contains("Save marks"));
    }
}
