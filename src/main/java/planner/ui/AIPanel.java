package planner.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import planner.ai.ClaudeService;
import planner.ai.DiagramData;
import planner.canvas.DiagramCanvas;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AIPanel extends JPanel {

    private final DiagramCanvas  canvas;
    private final ClaudeService  claude = new ClaudeService();
    private final ObjectMapper   mapper = new ObjectMapper();
    private final JTextArea      inputArea;
    private final JButton        generateBtn;
    private final JLabel         statusLabel;
    private final JRadioButton   autoRadio;
    private final JRadioButton   replaceRadio;
    private final JRadioButton   addRadio;
    private final JRadioButton   editRadio;
    private final JRadioButton   askRadio;
    private final JTextArea      responseArea;
    private final JScrollPane    responseScroll;
    private final JButton        clearChatBtn;

    private static final Color BG        = new Color(32, 32, 32);
    private static final Color BG_FIELD  = new Color(45, 45, 45);
    private static final Color FG_LABEL  = new Color(160, 160, 160);
    private static final Color BORDER_C  = new Color(60, 60, 60);

    public AIPanel(DiagramCanvas canvas) {
        this.canvas = canvas;
        setLayout(null);
        setBackground(BG);
        setPreferredSize(new Dimension(288, 0));

        // ── Section label ──────────────────────────────────────────────────────
        JLabel title = new JLabel("Describe your program:");
        title.setForeground(FG_LABEL);
        title.setFont(new Font("SansSerif", Font.PLAIN, 11));
        title.setBounds(10, 10, 268, 16);
        add(title);

        // ── Input area ─────────────────────────────────────────────────────────
        inputArea = new JTextArea();
        inputArea.setBackground(BG_FIELD);
        inputArea.setForeground(Color.WHITE);
        inputArea.setCaretColor(Color.WHITE);
        inputArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(inputArea);
        scroll.setBounds(10, 30, 268, 330);
        scroll.setBorder(new LineBorder(BORDER_C, 1));
        scroll.setBackground(BG_FIELD);
        scroll.getViewport().setBackground(BG_FIELD);
        add(scroll);

        // ── Mode toggle ────────────────────────────────────────────────────────
        JLabel modeLabel = new JLabel("AI action:");
        modeLabel.setForeground(FG_LABEL);
        modeLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        modeLabel.setBounds(10, 370, 268, 16);
        add(modeLabel);

        autoRadio    = radio("Auto (recommended)");
        replaceRadio = radio("Replace diagram");
        addRadio     = radio("Add to diagram");
        editRadio    = radio("Edit diagram");
        askRadio     = radio("Ask a question");
        autoRadio.setSelected(true);

        ButtonGroup group = new ButtonGroup();
        group.add(autoRadio);
        group.add(replaceRadio);
        group.add(addRadio);
        group.add(editRadio);
        group.add(askRadio);

        autoRadio.setBounds(   10, 388, 268, 22);
        replaceRadio.setBounds(10, 410, 130, 22);
        addRadio.setBounds(   148, 410, 130, 22);
        editRadio.setBounds(   10, 432, 130, 22);
        askRadio.setBounds(   148, 432, 130, 22);
        add(autoRadio);
        add(replaceRadio);
        add(addRadio);
        add(editRadio);
        add(askRadio);

        // ── Generate button ────────────────────────────────────────────────────
        generateBtn = new JButton("Generate");
        generateBtn.setBounds(10, 462, 268, 36);
        generateBtn.setBackground(new Color(70, 70, 70));
        generateBtn.setForeground(Color.WHITE);
        generateBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        generateBtn.setFocusPainted(false);
        generateBtn.setBorderPainted(false);
        generateBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        generateBtn.addActionListener(e -> generate());
        add(generateBtn);

        // ── Status label ───────────────────────────────────────────────────────
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(new Color(110, 110, 110));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLabel.setVerticalAlignment(SwingConstants.TOP);
        statusLabel.setBounds(10, 506, 268, 40);
        add(statusLabel);

        // ── Response area (shown after Ask questions) ─────────────────────────
        responseArea = new JTextArea();
        responseArea.setBackground(new Color(28, 28, 28));
        responseArea.setForeground(new Color(210, 210, 210));
        responseArea.setFont(new Font("SansSerif", Font.PLAIN, 11));
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);
        responseArea.setEditable(false);
        responseArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        responseScroll = new JScrollPane(responseArea);
        responseScroll.setBounds(10, 554, 268, 210);
        responseScroll.setBorder(new LineBorder(new Color(55, 55, 55), 1));
        responseScroll.setBackground(new Color(28, 28, 28));
        responseScroll.getViewport().setBackground(new Color(28, 28, 28));
        responseScroll.setVisible(false);
        add(responseScroll);

        clearChatBtn = new JButton("Clear Chat");
        clearChatBtn.setBounds(10, 772, 268, 28);
        clearChatBtn.setBackground(new Color(55, 55, 55));
        clearChatBtn.setForeground(new Color(150, 150, 150));
        clearChatBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        clearChatBtn.setFocusPainted(false);
        clearChatBtn.setBorderPainted(false);
        clearChatBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearChatBtn.addActionListener(e -> {
            claude.clearChatHistory();
            responseArea.setText("");
        });
        clearChatBtn.setVisible(false);
        add(clearChatBtn);


    }

    private void generate() {
        String prompt = inputArea.getText().trim();
        if (prompt.isEmpty()) {
            statusLabel.setText("Enter a description first.");
            return;
        }

        generateBtn.setEnabled(false);
        statusLabel.setForeground(new Color(120, 120, 120));
        statusLabel.setText("Thinking...");

        DiagramData current = canvas.getDiagramData();
        boolean hasContent  = current.classes != null && !current.classes.isEmpty();

        // ── Ask question mode ──────────────────────────────────────────────────
        if (askRadio.isSelected() || (autoRadio.isSelected() && looksLikeQuestion(prompt))) {
            responseScroll.setVisible(true);
            clearChatBtn.setVisible(true);
            revalidate();

            // Append "You:" label before streaming the answer
            SwingUtilities.invokeLater(() -> {
                if (!responseArea.getText().isEmpty()) responseArea.append("\n\n");
                responseArea.append("You: " + prompt + "\n\nAI: ");
                responseArea.setCaretPosition(responseArea.getDocument().getLength());
            });

            new Thread(() -> {
                try {
                    String currentJson = hasContent ? mapper.writeValueAsString(current) : "";
                    claude.chat(currentJson, prompt, token ->
                            SwingUtilities.invokeLater(() -> {
                                responseArea.append(token);
                                responseArea.setCaretPosition(responseArea.getDocument().getLength());
                            }));
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setForeground(new Color(90, 160, 90));
                        statusLabel.setText("Done.");
                        generateBtn.setEnabled(true);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setForeground(new Color(190, 80, 80));
                        statusLabel.setText("<html>Error: " + ex.getMessage() + "</html>");
                        generateBtn.setEnabled(true);
                    });
                }
            }, "claude-ask").start();
            return;
        }

        // ── Diagram generation / edit modes ────────────────────────────────────
        responseScroll.setVisible(false);
        clearChatBtn.setVisible(false);
        claude.clearChatHistory();

        // Auto: edit (replace) if canvas has content, otherwise generate fresh
        boolean doReplace = !addRadio.isSelected();
        boolean useEdit   = editRadio.isSelected() || (autoRadio.isSelected() && hasContent);

        new Thread(() -> {
            try {
                DiagramData data;
                if (useEdit) {
                    data = claude.edit(mapper.writeValueAsString(current), prompt, token ->
                            SwingUtilities.invokeLater(() -> statusLabel.setText("Streaming...")));
                } else {
                    String fullPrompt = prompt;
                    if (hasContent && addRadio.isSelected()) {
                        fullPrompt = "Current diagram:\n" + mapper.writeValueAsString(current)
                                + "\n\nInstruction: " + prompt;
                    }
                    data = claude.generate(fullPrompt, token ->
                            SwingUtilities.invokeLater(() -> statusLabel.setText("Streaming...")));
                }

                SwingUtilities.invokeLater(() -> {
                    int count = data.classes != null ? data.classes.size() : 0;
                    canvas.applyDiagram(data, doReplace);
                    statusLabel.setForeground(new Color(90, 160, 90));
                    statusLabel.setText("Done — " + count + " class" + (count == 1 ? "" : "es") + ".");
                    generateBtn.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setForeground(new Color(190, 80, 80));
                    statusLabel.setText("<html>Error: " + ex.getMessage() + "</html>");
                    generateBtn.setEnabled(true);
                });
            }
        }, "claude-generate").start();
    }

    private static boolean looksLikeQuestion(String prompt) {
        String lower = prompt.toLowerCase().stripLeading();
        // Ends with a question mark
        if (prompt.endsWith("?")) return true;
        // Starts with a question word
        String[] starters = {"what", "why", "how", "when", "where", "who", "which",
                             "is ", "are ", "does ", "do ", "can ", "could ", "should ",
                             "explain", "describe", "tell me"};
        for (String s : starters) {
            if (lower.startsWith(s)) return true;
        }
        return false;
    }

    public void scanProject() {
        File ideaProjects = new File(System.getProperty("user.home") + "/IdeaProjects");
        JFileChooser fc = new JFileChooser(ideaProjects.exists() ? ideaProjects : new File(System.getProperty("user.home")));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Select project root directory");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File root = fc.getSelectedFile();
        List<File> javaFiles = new ArrayList<>();
        collectJavaFiles(root, javaFiles);

        if (javaFiles.isEmpty()) {
            statusLabel.setForeground(new Color(190, 80, 80));
            statusLabel.setText("No .java files found.");
            return;
        }

        // Concatenate sources up to ~80 KB
        StringBuilder sb = new StringBuilder();
        int totalChars = 0;
        int included = 0;
        for (File f : javaFiles) {
            try {
                String content = Files.readString(f.toPath());
                if (totalChars + content.length() > 80_000) {
                    sb.append("\n[... truncated — too many files ...]");
                    break;
                }
                String rel = root.toPath().relativize(f.toPath()).toString();
                sb.append("=== ").append(rel).append(" ===\n").append(content).append("\n\n");
                totalChars += content.length();
                included++;
            } catch (IOException ignored) {}
        }

        String prompt = "Analyze this Java project and generate a class diagram:\n\n" + sb;

        generateBtn.setEnabled(false);
        statusLabel.setForeground(new Color(120, 120, 120));
        statusLabel.setText("Scanning " + included + " file" + (included == 1 ? "" : "s") + "...");

        new Thread(() -> {
            try {
                DiagramData data = claude.generate(prompt, token ->
                        SwingUtilities.invokeLater(() -> statusLabel.setText("Streaming...")));
                SwingUtilities.invokeLater(() -> {
                    int count = data.classes != null ? data.classes.size() : 0;
                    canvas.applyDiagram(data, true);
                    statusLabel.setForeground(new Color(90, 160, 90));
                    statusLabel.setText("Done — " + count + " class" + (count == 1 ? "" : "es") + " from project.");
                    generateBtn.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setForeground(new Color(190, 80, 80));
                    statusLabel.setText("<html>Error: " + ex.getMessage() + "</html>");
                    generateBtn.setEnabled(true);
                });
            }
        }, "claude-scan").start();
    }

    private void collectJavaFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (f.isDirectory() && !name.equals(".git") && !name.equals("target")
                    && !name.equals("build") && !name.equals("out")) {
                collectJavaFiles(f, result);
            } else if (f.isFile() && name.endsWith(".java")) {
                result.add(f);
            }
        }
    }

    private JRadioButton radio(String text) {
        JRadioButton rb = new JRadioButton(text);
        rb.setBackground(BG);
        rb.setForeground(new Color(150, 150, 150));
        rb.setFont(new Font("SansSerif", Font.PLAIN, 11));
        rb.setFocusPainted(false);
        return rb;
    }
}
