import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP tests run a separate JVM with a temporary data file. */
class BackendIntegrationTest {
    @TempDir Path temp;
    Process server;
    int port;
    HttpClient client;
    String token;
    @BeforeEach void start() throws Exception {
        try(var socket=new java.net.ServerSocket(0,0,InetAddress.getLoopbackAddress())) { port=socket.getLocalPort(); }
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(3)).build();
        launch();
    }
    void launch() throws Exception {
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
            "-cp",Path.of("target/classes").toAbsolutePath().toString(),"StudentApp",String.valueOf(port));
        builder.environment().put("STUDENT_DATA",temp.resolve("store.properties").toString());
        builder.environment().put("STUDENT_ADMIN_USER","testadmin");
        builder.environment().put("STUDENT_ADMIN_PASS","testpass");
        server=builder.redirectErrorStream(true).redirectOutput(temp.resolve("server.log").toFile()).start();
        long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        while(System.nanoTime()<deadline) {
            try { if(get("/health").statusCode()==200)return; } catch(Exception ignored) {}
            if(!server.isAlive())fail(Files.readString(temp.resolve("server.log")));
            Thread.sleep(50);
        }
        fail("Test server did not start");
    }
    @AfterEach void stop() throws Exception {
        if(server!=null) { server.destroy(); if(!server.waitFor(5,java.util.concurrent.TimeUnit.SECONDS))server.destroyForcibly().waitFor(); }
    }
    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(String path,Map<String,String> values) throws Exception {
        String body=values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),java.nio.charset.StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),java.nio.charset.StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(5)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    void login() throws Exception {
        assertEquals(200,post("/login",Map.of("username","testadmin","password","testpass")).statusCode());
        var matcher=Pattern.compile("name='csrf' value='([^']+)'").matcher(get("/register").body());
        assertTrue(matcher.find());token=matcher.group(1);
    }
    HttpResponse<String> save(String path,String... pairs) throws Exception {
        Map<String,String> fields=new HashMap<>();fields.put("csrf",token);
        for(int i=0;i<pairs.length;i+=2)fields.put(pairs[i],pairs[i+1]);
        return post(path,fields);
    }
    void seed() throws Exception {
        login();
        assertEquals(200,save("/register","id","24MIS0002","name","Test Student").statusCode());
        for(String code:List.of("CSE101","CSE102")) {
            assertEquals(200,save("/courses","code",code,"name",code).statusCode());
            assertEquals(200,save("/enrollment","id","24MIS0002","code",code).statusCode());
        }
    }
    @Test void authenticationCsrfAndLogoutProtectBackend() throws Exception {
        assertEquals(401,get("/students").statusCode());
        assertEquals(401,post("/login",Map.of("username","bad","password","bad")).statusCode());
        login();
        assertEquals(403,post("/register",Map.of("id","24MIS0002","name","Test")).statusCode());
        assertEquals(200,save("/logout").statusCode());
        assertEquals(401,get("/students").statusCode());
    }
    @Test void validationRejectsDuplicatesAndInvalidAcademicRecords() throws Exception {
        seed();
        assertEquals(409,save("/register","id","24MIS0002","name","Duplicate").statusCode());
        assertEquals(400,save("/register","id","!","name","Invalid").statusCode());
        assertEquals(400,save("/marks","id","24MIS0002","code","CSE101","mark","101").statusCode());
        assertEquals(400,save("/attendance","id","24MIS0002","code","CSE101","attended","41","conducted","40").statusCode());
        assertEquals(409,save("/marks","id","UNKNOWN","code","CSE101","mark","80").statusCode());
    }
    @Test void subjectReportsKeepDifferentCourseResultsSeparate() throws Exception {
        seed();
        for(String code:List.of("CSE101","CSE102")) {
            assertEquals(200,save("/attendance","id","24MIS0002","code",code,"attended","30","conducted","40").statusCode());
            assertEquals(200,save("/marks","id","24MIS0002","code",code,"mark",code.equals("CSE101")?"80":"35").statusCode());
        }
        String first=get("/reports?subject=CSE101").body(),second=get("/reports?subject=CSE102").body();
        assertTrue(first.contains("<td>80</td><td>Pass</td>"));
        assertFalse(first.contains("<td>35</td>"));
        assertTrue(second.contains("<td>35</td><td>Fail</td>"));
        assertFalse(second.contains("<td>80</td>"));
        assertTrue(first.contains("30/40 (75%)"));
    }
    @Test void enrollmentRemovalClearsRelatedRecordsAndAllowsDeletion() throws Exception {
        seed();
        assertEquals(409,save("/students","id","24MIS0002","op","delete").statusCode());
        assertEquals(200,save("/marks","id","24MIS0002","code","CSE101","mark","80").statusCode());
        assertEquals(200,save("/enrollment","id","24MIS0002","code","CSE101","op","delete").statusCode());
        assertFalse(get("/reports?subject=CSE101").body().contains("<td>24MIS0002</td>"));
        save("/enrollment","id","24MIS0002","code","CSE101");
        assertTrue(get("/reports?subject=CSE101").body().contains("<td>Pending</td>"));
        save("/enrollment","id","24MIS0002","code","CSE101","op","delete");
        save("/enrollment","id","24MIS0002","code","CSE102","op","delete");
        assertEquals(200,save("/students","id","24MIS0002","op","delete").statusCode());
        assertFalse(get("/students").body().contains("<td>24MIS0002</td>"));
    }
    @Test void recordsSurviveRealServerRestart() throws Exception {
        seed();
        save("/attendance","id","24MIS0002","code","CSE101","attended","30","conducted","40");
        save("/marks","id","24MIS0002","code","CSE101","mark","80");
        stop();launch();login();
        String report=get("/reports?subject=CSE101").body();
        assertTrue(report.contains("<td>Test Student</td>"));
        assertTrue(report.contains("<td>30/40 (75%)</td>"));
        assertTrue(report.contains("<td>80</td><td>Pass</td>"));
    }
}
