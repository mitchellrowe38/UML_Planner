package planner.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClaudeService {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String MODEL    = "claude-opus-4-6";

    private static final String SYSTEM_PROMPT = """
You are a Java class diagram generator. Respond with ONLY a JSON object — no prose, no markdown fences, no explanation before or after.

JSON schema:
{
  "classes": [
    {
      "id": "<camelCase className used as unique id>",
      "className": "<ClassName>",
      "fields": ["<visibility> <name>: <Type>", ...],
      "methods": ["<visibility> <name>(<params>): <ReturnType>", ...]
    }
  ],
  "connections": [
    {
      "fromId": "<id>",
      "toId": "<id>",
      "label": "<relationship>",
      "fromAnchorMember": "<exact field or method string from the SOURCE class's fields/methods array that originates this relationship, or omit>",
      "toAnchorMember": "<exact field or method string from the TARGET class's fields/methods array that is referenced, or omit>"
    }
  ]
}

Rules:
- Use UML visibility: + (public), - (private), # (protected)
- Connection label must be one of: extends, implements, uses, has, creates
- Do not include x or y coordinates
- Output raw JSON only — no markdown, no explanation
- Set fromAnchorMember to the exact member in the source class where this relationship originates: the field that holds the reference, or the method that uses/creates the other class. Always set this unless the relationship is extends/implements.
- Set toAnchorMember only when pointing at a specific named member of the target class (e.g. a method being called). Omit it when the relationship is to the class as a whole — storing an instance, creating one, or inheriting from it. When in doubt, omit toAnchorMember.
- The message may begin with "Current diagram:" showing what is already on the canvas. Use it as context when relevant.
""";

    private static final String EDIT_SYSTEM_PROMPT = """
You are a Java class diagram editor. You will receive the current diagram as JSON and a user instruction. Apply the changes and return the complete updated diagram as raw JSON using the same schema.

- Set fromAnchorMember to the exact member in the source class where this relationship originates: the field that holds the reference, or the method that uses/creates the other class. Always set this unless the relationship is extends/implements.
- Set toAnchorMember only when pointing at a specific named member of the target class (e.g. a method being called). Omit it when the relationship is to the class as a whole. When in doubt, omit toAnchorMember.
- All anchor strings must be copied verbatim from the relevant class's fields or methods array.

Do not add explanation. Output raw JSON only.""";

    private static final String CHAT_SYSTEM_PROMPT = """
You are a helpful assistant for Java software design and architecture. The user may provide their current class diagram as JSON for context. Answer their questions clearly and concisely in plain text.

When the user asks you to add, modify, or remove classes, fields, methods, or connections, output the complete updated diagram JSON wrapped in <diagram> tags (JSON on its own line, closing tag on its own line). Use the same schema as the current diagram — include ALL existing classes plus your changes. Precede the tags with a brief plain-text explanation of what changed. The app will apply the diagram automatically.

Do not output raw JSON outside of <diagram> tags.""";

    private String apiKey;
    private final List<Map<String, Object>> chatHistory = new ArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient   http   = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public ClaudeService() {
        apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) apiKey = readFromKeychain();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = promptForKey();
            if (apiKey != null && !apiKey.isBlank()) saveToKeychain(apiKey);
        }
    }

    private static String readFromKeychain() {
        try {
            Process p = new ProcessBuilder(
                    "security", "find-generic-password",
                    "-a", "JavaPlanner", "-s", "anthropic-api-key", "-w")
                    .start();
            String val = new String(p.getInputStream().readAllBytes()).trim();
            return p.waitFor() == 0 && !val.isBlank() ? val : null;
        } catch (Exception e) { return null; }
    }

    private static void saveToKeychain(String key) {
        try {
            // Delete any existing entry first (so update works)
            new ProcessBuilder("security", "delete-generic-password",
                    "-a", "JavaPlanner", "-s", "anthropic-api-key")
                    .start().waitFor();
            new ProcessBuilder("security", "add-generic-password",
                    "-a", "JavaPlanner", "-s", "anthropic-api-key", "-w", key)
                    .start().waitFor();
        } catch (Exception ignored) {}
    }

    private String promptForKey() {
        JPasswordField pf = new JPasswordField(40);
        pf.setFont(pf.getFont().deriveFont(14f));
        int result = JOptionPane.showConfirmDialog(null, pf,
                "Enter your ANTHROPIC_API_KEY",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return result == JOptionPane.OK_OPTION ? new String(pf.getPassword()).trim() : "";
    }

    @FunctionalInterface
    public interface StreamCallback {
        void onToken(String token);
    }

    // ── Shared streaming core ──────────────────────────────────────────────────
    private String streamText(String systemPrompt, String userMessage, StreamCallback callback) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "model",    MODEL,
                "max_tokens", 16000,
                "thinking", Map.of("type", "enabled", "budget_tokens", 5000),
                "stream",   true,
                "system",   systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("x-api-key",           apiKey)
                .header("anthropic-version",   "2023-06-01")
                .header("content-type",        "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<java.util.stream.Stream<String>> response =
                http.send(request, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() != 200) {
            String err = response.body().collect(Collectors.joining("\n"));
            throw new IOException("Claude API error " + response.statusCode() + ": " + err);
        }

        StringBuilder accumulated = new StringBuilder();
        response.body().forEach(line -> {
            if (!line.startsWith("data: ")) return;
            String json = line.substring(6).trim();
            if (json.equals("[DONE]")) return;
            try {
                JsonNode node  = mapper.readTree(json);
                JsonNode delta = node.path("delta");
                if ("text_delta".equals(delta.path("type").asText())) {
                    String token = delta.path("text").asText();
                    accumulated.append(token);
                    if (callback != null) callback.onToken(token);
                }
            } catch (Exception ignored) {}
        });

        return accumulated.toString().trim();
    }

    // ── Multi-turn chat streaming (no extended thinking) ───────────────────────
    private String streamChat(List<Map<String, Object>> messages, StreamCallback callback) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "model",      MODEL,
                "max_tokens", 8000,
                "stream",     true,
                "system",     CHAT_SYSTEM_PROMPT,
                "messages",   messages
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("x-api-key",           apiKey)
                .header("anthropic-version",   "2023-06-01")
                .header("content-type",        "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<java.util.stream.Stream<String>> response =
                http.send(request, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() != 200) {
            String err = response.body().collect(Collectors.joining("\n"));
            throw new IOException("Claude API error " + response.statusCode() + ": " + err);
        }

        StringBuilder accumulated = new StringBuilder();
        response.body().forEach(line -> {
            if (!line.startsWith("data: ")) return;
            String json = line.substring(6).trim();
            if (json.equals("[DONE]")) return;
            try {
                JsonNode node  = mapper.readTree(json);
                JsonNode delta = node.path("delta");
                if ("text_delta".equals(delta.path("type").asText())) {
                    String token = delta.path("text").asText();
                    accumulated.append(token);
                    if (callback != null) callback.onToken(token);
                }
            } catch (Exception ignored) {}
        });

        return accumulated.toString().trim();
    }

    // ── Public API ─────────────────────────────────────────────────────────────
    public DiagramData generate(String userPrompt, StreamCallback callback) throws Exception {
        String text = streamText(SYSTEM_PROMPT, userPrompt, callback);
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        if (start < 0 || end < 0) throw new IOException("No JSON object in response:\n" + text);
        return mapper.readValue(text.substring(start, end + 1), DiagramData.class);
    }

    public DiagramData edit(String currentJson, String instruction, StreamCallback callback) throws Exception {
        String userMessage = "Current diagram:\n" + currentJson + "\n\nInstruction: " + instruction;
        String text = streamText(EDIT_SYSTEM_PROMPT, userMessage, callback);
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        if (start < 0 || end < 0) throw new IOException("No JSON object in response:\n" + text);
        return mapper.readValue(text.substring(start, end + 1), DiagramData.class);
    }

    /** Ask a plain-text question about the diagram. Maintains conversation history across calls. */
    public String chat(String currentJson, String question, StreamCallback callback) throws Exception {
        String userContent = currentJson.isBlank()
                ? question
                : "Current diagram:\n" + currentJson + "\n\nQuestion: " + question;

        chatHistory.add(Map.of("role", "user", "content", userContent));

        String answer = streamChat(List.copyOf(chatHistory), callback);

        chatHistory.add(Map.of("role", "assistant", "content", answer));
        return answer;
    }

    public void clearChatHistory() { chatHistory.clear(); }

    /** Returns the chat conversation as plain text for use as context in edit/generate calls. */
    public String getChatContext() {
        if (chatHistory.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> msg : chatHistory) {
            String role    = (String) msg.get("role");
            String content = (String) msg.get("content");
            // Strip the "Current diagram: ..." preamble injected into user messages
            if ("user".equals(role) && content.contains("\n\nQuestion: ")) {
                content = content.substring(content.indexOf("\n\nQuestion: ") + 12);
            } else if ("user".equals(role) && content.startsWith("Current diagram:") && !content.contains("\n\nQuestion: ")) {
                continue; // diagram-only message with no question, skip
            }
            sb.append("user".equals(role) ? "User: " : "Assistant: ").append(content).append("\n");
        }
        return sb.toString().trim();
    }
}
