import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Local educational application. Not designed for internet exposure. */
public class StudentApp {
    static final Map<String,String> students = new TreeMap<>();
    static final Map<String,String> courses = new TreeMap<>();
    static final Set<String> enrollments = new TreeSet<>();
    static final Map<String,String> attendance = new TreeMap<>();
    static final Map<String,Integer> marks = new TreeMap<>();
    static final Map<String,String> sessions = new ConcurrentHashMap<>();
    static final Object LOCK = new Object();
    static final Path STORE = Paths.get(System.getenv().getOrDefault("STUDENT_DATA", "data/store.properties"));
    static final String ADMIN_USER = System.getenv().getOrDefault("STUDENT_ADMIN_USER", "admin");
    static final String ADMIN_PASS = System.getenv().getOrDefault("STUDENT_ADMIN_PASS", "Demo123!");

    static boolean validId(String s) { return s != null && s.matches("[A-Za-z0-9-]{3,20}"); }
    static boolean validName(String s) { return s != null && !s.strip().isEmpty() && s.strip().length() <= 100 && s.chars().noneMatch(Character::isISOControl); }
    static int percent(int attended, int total) { if(total<=0 || attended<0 || attended>total) throw new IllegalArgumentException("Invalid attendance"); return (int)Math.round(100.0*attended/total); }
    static boolean validMark(int m) { return m>=0 && m<=100; }
    static String result(Integer m) { return m == null ? "Pending" : m < 40 ? "Fail" : "Pass"; }
    static String key(String student,String course) { return student+"|"+course; }
    static String decode(String s) { return URLDecoder.decode(s,StandardCharsets.UTF_8); }
    static Map<String,String> params(String raw) { Map<String,String> m=new HashMap<>(); if(raw==null||raw.isEmpty())return m; for(String x:raw.split("&")){String[] p=x.split("=",2);m.put(decode(p[0]),p.length>1?decode(p[1]):"");}return m; }
    static List<String[]> subjectRows(String subject, boolean report) {
        List<String[]> records=new ArrayList<>();
        for(String k:enrollments) {
            String[] pair=k.split("\\|",2);
            if(!pair[1].equals(subject))continue;
            Integer mark=marks.get(k);
            records.add(report ? new String[]{pair[0],students.get(pair[0]),attendance.getOrDefault(k,"Not recorded"),mark==null?"Not recorded":String.valueOf(mark),result(mark)} : new String[]{pair[0],students.get(pair[0])});
        }
        records.sort(Comparator.comparing(row->row[0]));
        return records;
    }
    static void response(HttpExchange e,int status,String title,String text){try {byte[] raw=Frontend.page(title,text).getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");e.sendResponseHeaders(status,raw.length);try(OutputStream out=e.getResponseBody()){out.write(raw);}}catch(IOException ex){throw new UncheckedIOException(ex);}}
    static void save() throws IOException {Properties p=new Properties(); students.forEach((k,v)->p.setProperty("student."+k,v));courses.forEach((k,v)->p.setProperty("course."+k,v));for(String k:enrollments)p.setProperty("enrollment."+k,"yes");attendance.forEach((k,v)->p.setProperty("attendance."+k,v));marks.forEach((k,v)->p.setProperty("marks."+k,String.valueOf(v)));Path parent=STORE.toAbsolutePath().getParent();Files.createDirectories(parent);Path tmp=Files.createTempFile(parent,"sms-",".tmp");try(OutputStream out=Files.newOutputStream(tmp)){p.store(out,"Student Management System");}try{Files.move(tmp,STORE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,STORE,StandardCopyOption.REPLACE_EXISTING);}}
    static void load() throws IOException {if(!Files.exists(STORE))return;Properties p=new Properties();try(InputStream in=Files.newInputStream(STORE)){p.load(in);}students.clear();courses.clear();enrollments.clear();attendance.clear();marks.clear();for(String k:p.stringPropertyNames()){String v=p.getProperty(k);if(k.startsWith("student."))students.put(k.substring(8),v);else if(k.startsWith("course."))courses.put(k.substring(7),v);else if(k.startsWith("enrollment."))enrollments.add(k.substring(11));else if(k.startsWith("attendance."))attendance.put(k.substring(11),v);else if(k.startsWith("marks."))marks.put(k.substring(6),Integer.parseInt(v));}}
    static String cookie(HttpExchange e){List<String> c=e.getRequestHeaders().get("Cookie");if(c==null)return "";for(String header:c)for(String part:header.split(";")){String x=part.strip();if(x.startsWith("SID="))return x.substring(4);}return "";}
    static String csrf(HttpExchange e){return sessions.get(cookie(e));}
    static void handle(HttpExchange e){synchronized(LOCK){String path=e.getRequestURI().getPath();String method=e.getRequestMethod();String title=path.equals("/")?"Dashboard":path.substring(1);int status=200;String body="";
        try{
            if(!method.equals("GET")&&!method.equals("POST")){response(e,405,"Method not allowed","Unsupported method");return;}
            if(path.equals("/health")){response(e,200,"Health","OK");return;}
            String request="";if(method.equals("POST")){byte[] bytes=e.getRequestBody().readNBytes(8193);if(bytes.length>8192){response(e,413,"Payload too large","Request limit exceeded");return;}request=new String(bytes,StandardCharsets.UTF_8);}
            Map<String,String> p=params(request);
            if(path.equals("/login")){
                if(method.equals("POST")){if(ADMIN_USER.equals(p.get("username"))&&ADMIN_PASS.equals(p.get("password"))){String sid=UUID.randomUUID().toString(),token=UUID.randomUUID().toString();sessions.put(sid,token);e.getResponseHeaders().add("Set-Cookie","SID="+sid+"; HttpOnly; SameSite=Strict; Path=/");body=Frontend.loginSuccess();}else{status=401;body=Frontend.message("Invalid credentials",false);}}
                else body=Frontend.login();
                response(e,status,"Login",body);return;
            }
            String selected=params(e.getRequestURI().getRawQuery()).getOrDefault("subject",p.getOrDefault("code",""));
            String token=csrf(e);
            if(token==null){response(e,401,"Unauthorized",Frontend.signInRequired());return;}
            if(method.equals("POST")&&!token.equals(p.get("csrf"))){response(e,403,"Forbidden","Invalid CSRF token");return;}
            if(path.equals("/logout")){if(method.equals("POST")){sessions.remove(cookie(e));e.getResponseHeaders().add("Set-Cookie","SID=; Max-Age=0; Path=/");body="Logged out";}else body=Frontend.form("/logout","","Confirm logout",token);}
            else if(path.equals("/")){body=Frontend.dashboard(students.size(),courses.size(),enrollments.size());}
            else if(path.equals("/register")){if(method.equals("POST")){String id=p.getOrDefault("id","").strip(),name=p.getOrDefault("name","").strip();if(!validId(id)||!validName(name)){status=400;body="Invalid student identifier or name";}else if(students.containsKey(id)){status=409;body="Duplicate student";}else{students.put(id,name);save();body=Frontend.message("Student registered",true);}}body=Frontend.render(path,body,token,selected,students,courses,subjectRows(selected,false),subjectRows(selected,true));}
            else if(path.equals("/students")){if(method.equals("POST")){String id=p.getOrDefault("id",""),op=p.getOrDefault("op","");if(!students.containsKey(id)){status=404;body="Student not found";}else if(op.equals("delete")){if(enrollments.stream().anyMatch(x->x.startsWith(id+"|"))){status=409;body="Remove enrollment first";}else{students.remove(id);save();body="Student removed";}}else if(op.equals("update")&&validName(p.get("name"))){students.put(id,p.get("name").strip());save();body="Student updated";}else{status=400;body="Invalid operation";}}body=Frontend.render(path,body,token,selected,students,courses,subjectRows(selected,false),subjectRows(selected,true));}
            else if(path.equals("/courses")){if(method.equals("POST")){String code=p.getOrDefault("code","").strip(),name=p.getOrDefault("name","").strip(),op=p.getOrDefault("op","create");if(op.equals("delete")){if(!courses.containsKey(code)){status=404;body="Course not found";}else if(enrollments.stream().anyMatch(x->x.endsWith("|"+code))){status=409;body="Remove enrollment first";}else{courses.remove(code);save();body="Course deleted";}}else if(!validId(code)||!validName(name)){status=400;body="Invalid course";}else if(op.equals("update")&&courses.containsKey(code)){courses.put(code,name);save();body="Course updated";}else if(op.equals("create")&&!courses.containsKey(code)){courses.put(code,name);save();body="Course created";}else{status=409;body="Duplicate or missing course";}}body=Frontend.render(path,body,token,selected,students,courses,subjectRows(selected,false),subjectRows(selected,true));}
            else if(path.equals("/enrollment")){if(method.equals("POST")){String id=p.getOrDefault("id",""),code=p.getOrDefault("code",""),k=key(id,code);if(!students.containsKey(id)||!courses.containsKey(code)){status=404;body="Unknown student or course";}else if("delete".equals(p.get("op"))){enrollments.remove(k);attendance.remove(k);marks.remove(k);save();body="Enrollment removed";}else if(!enrollments.add(k)){status=409;body="Duplicate enrollment";}else{save();body="Enrollment saved";}}body=Frontend.render(path,body,token,selected,students,courses,subjectRows(selected,false),subjectRows(selected,true));}
            else if(path.equals("/attendance")){if(method.equals("POST")){String k=key(p.getOrDefault("id",""),p.getOrDefault("code",""));if(!enrollments.contains(k)){status=409;body="Enrollment required";}else{try{int a=Integer.parseInt(p.get("attended")),t=Integer.parseInt(p.get("conducted"));int pc=percent(a,t);attendance.put(k,a+"/"+t+" ("+pc+"%)");save();body="Attendance saved: "+pc+"%";}catch(IllegalArgumentException ex){status=400;body="Invalid attendance range or numbers";}}}body=Frontend.render(path,body,token,selected,students,courses,subjectRows(selected,false),subjectRows(selected,true));}
            else if(path.equals("/marks")){if(method.equals("POST")){String k=key(p.getOrDefault("id",""),p.getOrDefault("code",""));if(!enrollments.contains(k)){status=409;body="Enrollment required";}else{try{int mark=Integer.parseInt(p.get("mark"));if(!validMark(mark))throw new IllegalArgumentException();marks.put(k,mark);save();body="Marks saved";}catch(RuntimeException ex){status=400;body="Invalid mark";}}}body=Frontend.render(path,body,token,selected,students,courses,subjectRows(selected,false),subjectRows(selected,true));}
            else if(path.equals("/reports")){body=Frontend.render(path,body,token,selected,students,courses,subjectRows(selected,false),subjectRows(selected,true));}
            else{status=404;body="Page not found";}
        } catch(IllegalArgumentException ex){status=400;body="Invalid input";}catch(Exception ex){status=500;body="Unable to complete operation";}
        response(e,status,title,body);
    }}
    public static void main(String[] args)throws Exception {load();int port=args.length>0?Integer.parseInt(args[0]):8188;HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);server.createContext("/",StudentApp::handle);server.setExecutor(Executors.newFixedThreadPool(8));server.start();System.out.println("Running at http://127.0.0.1:"+port);}
}
