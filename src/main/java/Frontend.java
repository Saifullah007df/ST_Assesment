import java.util.*;

/** HTML, CSS, forms, and tables for the student-management interface. */
final class Frontend {
    private Frontend() {}
    static final String CSS = "body{font:16px Arial;margin:0;background:#f4f6f8;color:#182432}header{background:#17324f;color:white;padding:22px 5%}nav{background:white;padding:16px 5%;border-bottom:1px solid #ddd}nav a{margin-right:16px}main{max-width:1000px;margin:28px auto;padding:24px;background:white;border:1px solid #ddd}input,button,select{font:inherit;padding:8px;margin:5px}button{background:#17324f;color:white;border:0}table{width:100%;border-collapse:collapse}td,th{border:1px solid #ccc;padding:9px;text-align:left}.ok{background:#e4f5e8;padding:8px}.error{background:#fae6e6;padding:8px}";
    static String esc(Object value) { String s=String.valueOf(value); return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
    static String field(String name,String title) { return "<label>"+title+" <input name='"+name+"' required></label>"; }
    static String form(String action,String fields,String button,String csrf) { return "<form action='"+action+"' method='post'><input type='hidden' name='csrf' value='"+esc(csrf)+"'>"+fields+"<button>"+button+"</button></form>"; }
    static String table(String[] heads,Collection<String[]> data){StringBuilder b=new StringBuilder("<table><tr>");for(String h:heads)b.append("<th>").append(esc(h)).append("</th>");b.append("</tr>");for(String[] row:data){b.append("<tr>");for(String v:row)b.append("<td>").append(esc(v)).append("</td>");b.append("</tr>");}return b.append("</table>").toString();}
    static List<String[]> rows(Map<String,? extends Object> records){List<String[]> r=new ArrayList<>();records.forEach((k,v)->r.add(new String[]{k,String.valueOf(v)}));return r;}
    static String page(String title,String body){return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>"+esc(title)+"</title><style>"+CSS+"</style></head><body><header><h1>Student Management System</h1><small>ISWE305P Software Testing</small></header><nav><a href='/'>Dashboard</a><a href='/login'>Login</a><a href='/register'>Registration</a><a href='/students'>Students</a><a href='/courses'>Courses</a><a href='/enrollment'>Enrollment</a><a href='/attendance'>Attendance</a><a href='/marks'>Marks</a><a href='/reports'>Reports</a><a href='/logout'>Logout</a></nav><main><h2>"+esc(title)+"</h2>"+body+"</main></body></html>";}
    static String subjectSelector(String path,String selected,Map<String,String> courses) {
        StringBuilder b=new StringBuilder("<form method='get' action='"+esc(path)+"'><label>Subject <select name='subject' onchange='this.form.submit()'><option value=''>Select a subject</option>");
        courses.forEach((code,name)->b.append("<option value='").append(esc(code)).append("'").append(code.equals(selected)?" selected":"").append(">").append(esc(code+" — "+name)).append("</option>"));
        return b.append("</select></label><button>View subject</button></form>").toString();
    }
    static String subjectField(String selected) {
        return "<input type='hidden' name='code' value='"+esc(selected)+"'>";
    }
    static String message(String text,boolean success) {
        return "<p class='"+(success?"ok":"error")+"'>"+esc(text)+"</p>";
    }
    static String loginSuccess() {
        return "<p class='ok'>Authentication accepted. <a href='/'>Open dashboard</a></p>";
    }
    static String signInRequired() {
        return "Sign in at <a href='/login'>Login</a>.";
    }
    static String login() {
        return "<form method='post'>"+field("username","Username")+"<label>Password <input type='password' name='password' required></label><button>Login</button></form>";
    }
    static String dashboard(int students,int courses,int enrollments) {
        return "<p>Manage student records, courses, enrollment, attendance, marks, and reports.</p><p>Students: "+students+" | Courses: "+courses+" | Enrollments: "+enrollments+"</p>";
    }
    static String render(String path,String body,String token,String selected,
            Map<String,String> students,Map<String,String> courses,List<String[]> enrolled,
            List<String[]> report) {
        switch(path) {
            case "/register" -> { body+=form("/register",field("id","Register number")+field("name","Student name"),"Add student",token); }
            case "/students" -> { body+=table(new String[]{"Student ID","Name"},rows(students));body+="<h3>Update student</h3>"+form("/students",field("id","ID")+field("name","New name")+"<input type='hidden' name='op' value='update'>","Update",token)+"<h3>Delete student</h3>"+form("/students",field("id","ID")+"<input type='hidden' name='op' value='delete'>","Delete",token); }
            case "/courses" -> { body+=table(new String[]{"Course Code","Title"},rows(courses));body+="<h3>Add course</h3>"+form("/courses",field("code","Code")+field("name","Title"),"Add",token)+"<h3>Edit course</h3>"+form("/courses",field("code","Code")+field("name","Title")+"<input type='hidden' name='op' value='update'>","Update",token)+"<h3>Delete course</h3>"+form("/courses",field("code","Code")+"<input type='hidden' name='op' value='delete'>","Delete",token); }
            case "/enrollment" -> { body+=subjectSelector(path,selected,courses);if(courses.containsKey(selected)){body+="<h3>Enrolled students</h3>"+table(new String[]{"Student ID","Student name"},enrolled)+form("/enrollment",field("id","Student ID")+subjectField(selected),"Enroll",token)+form("/enrollment",field("id","Student ID")+subjectField(selected)+"<input type='hidden' name='op' value='delete'>","Remove enrollment",token);}else body+="<p>Select a subject to view and manage enrollment.</p>"; }
            case "/attendance" -> { body+=subjectSelector(path,selected,courses);if(courses.containsKey(selected)){List<String[]> records=new ArrayList<>();for(String[] row:report)records.add(new String[]{row[0],row[1],row[2]});body+=table(new String[]{"Student ID","Student name","Attendance"},records)+form("/attendance",field("id","Student ID")+subjectField(selected)+field("attended","Attended")+field("conducted","Conducted"),"Save attendance",token);}else body+="<p>Select a subject to view and record attendance.</p>"; }
            case "/marks" -> { body+=subjectSelector(path,selected,courses);if(courses.containsKey(selected)){List<String[]> records=new ArrayList<>();for(String[] row:report)records.add(new String[]{row[0],row[1],row[3],row[4]});body+=table(new String[]{"Student ID","Student name","Mark / 100","Result"},records)+form("/marks",field("id","Student ID")+subjectField(selected)+field("mark","Mark / 100"),"Save marks",token);}else body+="<p>Select a subject to view and record marks.</p>"; }
            case "/reports" -> { body=subjectSelector(path,selected,courses);if(courses.containsKey(selected))body+=table(new String[]{"Student ID","Student name","Attendance","Marks","Result"},report);else body+="<p>Select a subject to view its report.</p>"; }
            default -> { }
        }
        return body;
    }
}
