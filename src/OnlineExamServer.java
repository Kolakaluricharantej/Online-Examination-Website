import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OnlineExamServer {

    static class Question {
        String question;
        String[] options;
        int answer;

        Question(String question, String[] options, int answer) {
            this.question = question;
            this.options = options;
            this.answer = answer;
        }
    }

    static final List<Question> QUESTIONS = List.of(
        new Question("Which language is mainly used to style HTML pages?",
            new String[]{"Java", "CSS", "SQL", "Python"}, 1),

        new Question("Which HTML tag is used to create a hyperlink?",
            new String[]{"<link>", "<a>", "<href>", "<url>"}, 1),

        new Question("Which keyword is used to create a class in Java?",
            new String[]{"class", "Class", "new", "object"}, 0),

        new Question("Which method is the entry point of a Java application?",
            new String[]{"start()", "run()", "main()", "init()"}, 2),

        new Question("Which technology is used to make web pages interactive?",
            new String[]{"JavaScript", "CSS", "HTML only", "SQL"}, 0),

        new Question("Which symbol starts a single-line comment in Java?",
            new String[]{"<!--", "//", "#", "/*/"}, 1),

        new Question("Which CSS property changes the text color?",
            new String[]{"font-color", "text-color", "color", "foreground"}, 2),

        new Question("Which Java type stores true or false?",
            new String[]{"int", "boolean", "char", "double"}, 1),

        new Question("What does HTTP stand for?",
            new String[]{"HyperText Transfer Protocol",
                        "HighText Transfer Program",
                        "Hyper Transfer Text Process",
                        "Home Tool Transfer Protocol"}, 0),

        new Question("Which HTML element is commonly used for the largest heading?",
            new String[]{"<heading>", "<head>", "<h6>", "<h1>"}, 3)
    );

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);

        server.createContext("/api/login", OnlineExamServer::login);
        server.createContext("/api/questions", OnlineExamServer::questions);
        server.createContext("/api/submit", OnlineExamServer::submit);
        server.createContext("/", OnlineExamServer::staticFiles);

        server.setExecutor(null);
        server.start();

        System.out.println("======================================");
        System.out.println(" Online Examination Website Started");
        System.out.println(" Open: http://localhost:8081");
        System.out.println("======================================");
    }

    static void login(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            send(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }

        String body = readBody(exchange);
        String name = jsonValue(body, "name");
        String password = jsonValue(body, "password");

        if (name.isBlank() || password.isBlank()) {
            send(exchange, 400,
                "{\"success\":false,\"message\":\"Enter name and password.\"}");
            return;
        }

        // Academic demo login: password must be 1234.
        if (!password.equals("1234")) {
            send(exchange, 401,
                "{\"success\":false,\"message\":\"Invalid password. Use 1234 for the demo.\"}");
            return;
        }

        send(exchange, 200,
            "{\"success\":true,\"name\":\"" + jsonEscape(name) + "\"}");
    }

    static void questions(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            send(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < QUESTIONS.size(); i++) {
            Question q = QUESTIONS.get(i);
            if (i > 0) json.append(",");

            json.append("{\"question\":\"")
                .append(jsonEscape(q.question))
                .append("\",\"options\":[");

            for (int j = 0; j < q.options.length; j++) {
                if (j > 0) json.append(",");
                json.append("\"").append(jsonEscape(q.options[j])).append("\"");
            }
            json.append("]}");
        }
        json.append("]");

        send(exchange, 200, json.toString());
    }

    static void submit(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            send(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = readBody(exchange);
        String student = jsonValue(body, "student");
        String answersText = jsonArray(body, "answers");

        if (student.isBlank() || answersText == null) {
            send(exchange, 400, "{\"error\":\"Invalid submission\"}");
            return;
        }

        int[] answers = parseIntArray(answersText);

        int correct = 0;
        int unanswered = 0;
        List<String> review = new ArrayList<>();

        for (int i = 0; i < QUESTIONS.size(); i++) {
            int selected = i < answers.length ? answers[i] : -1;
            Question q = QUESTIONS.get(i);

            if (selected == -1) {
                unanswered++;
            } else if (selected == q.answer) {
                correct++;
            }

            String selectedText =
                selected >= 0 && selected < q.options.length
                    ? q.options[selected]
                    : "Not answered";

            review.add(
                "{\"question\":\"" + jsonEscape(q.question) +
                "\",\"selected\":" + selected +
                ",\"correct\":" + (selected == q.answer) +
                ",\"correctAnswer\":\"" + jsonEscape(q.options[q.answer]) +
                "\",\"selectedAnswer\":\"" + jsonEscape(selectedText) + "\"}"
            );
        }

        int total = QUESTIONS.size();
        int wrong = total - correct - unanswered;
        double percentage = total == 0 ? 0 : (correct * 100.0 / total);

        String json = "{"
            + "\"student\":\"" + jsonEscape(student) + "\","
            + "\"score\":" + correct + ","
            + "\"total\":" + total + ","
            + "\"correct\":" + correct + ","
            + "\"wrong\":" + wrong + ","
            + "\"unanswered\":" + unanswered + ","
            + "\"percentage\":" + String.format(Locale.US, "%.1f", percentage) + ","
            + "\"review\":[" + String.join(",", review) + "]"
            + "}";

        send(exchange, 200, json);
    }

    static void staticFiles(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/")) path = "/index.html";

        // Prevent path traversal.
        path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        if (path.contains("..")) {
            send(exchange, 403, "Forbidden");
            return;
        }

        Path file = Paths.get("web" + path).normalize();

        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            send(exchange, 404, "404 - File Not Found");
            return;
        }

        String contentType = contentType(file.toString());
        byte[] data = Files.readAllBytes(file);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    static String contentType(String file) {
        if (file.endsWith(".html")) return "text/html; charset=UTF-8";
        if (file.endsWith(".css")) return "text/css; charset=UTF-8";
        if (file.endsWith(".js")) return "application/javascript; charset=UTF-8";
        return "text/plain; charset=UTF-8";
    }

    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void send(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static String jsonValue(String json, String key) {
        Pattern p = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        );
        Matcher m = p.matcher(json);
        return m.find() ? jsonUnescape(m.group(1)) : "";
    }

    static String jsonArray(String json, String key) {
        Pattern p = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]"
        );
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static int[] parseIntArray(String text) {
        if (text == null || text.isBlank()) return new int[0];

        String[] parts = text.split(",");
        int[] result = new int[parts.length];

        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                result[i] = -1;
            }
        }
        return result;
    }

    static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    static String jsonUnescape(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r");
    }
}
