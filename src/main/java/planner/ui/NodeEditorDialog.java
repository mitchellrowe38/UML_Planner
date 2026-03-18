package planner.ui;

import planner.canvas.ObjectNode;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.stream.Collectors;

public class NodeEditorDialog extends JDialog {

    private final ObjectNode target;
    private final JTextField nameField;
    private final JTextArea  fieldsArea;
    private final JTextArea  methodsArea;

    private static final Color BG_DARK   = new Color(28, 28, 28);
    private static final Color BG_FIELD  = new Color(45, 45, 45);
    private static final Color FG_LABEL  = new Color(180, 180, 180);
    private static final Color FG_INPUT  = Color.WHITE;
    private static final Color BORDER_C  = new Color(70, 70, 70);
    private static final Color BTN_BG    = new Color(60, 60, 60);

    public NodeEditorDialog(Window owner, ObjectNode node) {
        super(owner, "Edit Class", ModalityType.APPLICATION_MODAL);
        this.target = node;

        setSize(420, 400);
        setLocationRelativeTo(owner);
        setResizable(false);
        setLayout(null);
        getContentPane().setBackground(BG_DARK);

        // ── Class name ────────────────────────────────────────────────────────
        JLabel nameLabel = label("Class Name", 16, 14);
        add(nameLabel);

        nameField = styledTextField(node.className);
        nameField.setBounds(16, 36, 388, 30);
        add(nameField);

        // ── Fields ────────────────────────────────────────────────────────────
        JLabel fieldsLabel = label("Fields  (one per line, e.g.  - name: String)", 16, 82);
        add(fieldsLabel);

        fieldsArea = styledTextArea(String.join("\n", node.fields));
        JScrollPane fieldsScroll = scroll(fieldsArea);
        fieldsScroll.setBounds(16, 100, 388, 90);
        add(fieldsScroll);

        // ── Methods ───────────────────────────────────────────────────────────
        JLabel methodsLabel = label("Methods  (one per line, e.g.  + getName(): String)", 16, 202);
        add(methodsLabel);

        methodsArea = styledTextArea(String.join("\n", node.methods));
        JScrollPane methodsScroll = scroll(methodsArea);
        methodsScroll.setBounds(16, 220, 388, 90);
        add(methodsScroll);

        // ── Buttons ───────────────────────────────────────────────────────────
        JButton saveBtn   = styledButton("Save");
        JButton cancelBtn = styledButton("Cancel");
        saveBtn.setBounds(16,  330, 185, 34);
        cancelBtn.setBounds(219, 330, 185, 34);
        add(saveBtn);
        add(cancelBtn);

        saveBtn.addActionListener(e -> save());
        cancelBtn.addActionListener(e -> dispose());
    }

    private void save() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Class name cannot be empty.");
            return;
        }
        target.className = name;
        target.fields = Arrays.stream(fieldsArea.getText().split("\n"))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        target.methods = Arrays.stream(methodsArea.getText().split("\n"))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        dispose();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private JLabel label(String text, int x, int y) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(FG_LABEL);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lbl.setBounds(x, y, 388, 16);
        return lbl;
    }

    private JTextField styledTextField(String text) {
        JTextField tf = new JTextField(text);
        tf.setBackground(BG_FIELD);
        tf.setForeground(FG_INPUT);
        tf.setCaretColor(FG_INPUT);
        tf.setFont(new Font("Monospaced", Font.PLAIN, 13));
        tf.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_C, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return tf;
    }

    private JTextArea styledTextArea(String text) {
        JTextArea ta = new JTextArea(text);
        ta.setBackground(BG_FIELD);
        ta.setForeground(FG_INPUT);
        ta.setCaretColor(FG_INPUT);
        ta.setFont(new Font("Monospaced", Font.PLAIN, 11));
        ta.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        ta.setLineWrap(false);
        return ta;
    }

    private JScrollPane scroll(JTextArea ta) {
        JScrollPane sp = new JScrollPane(ta);
        sp.setBorder(new LineBorder(BORDER_C, 1));
        sp.setBackground(BG_FIELD);
        sp.getViewport().setBackground(BG_FIELD);
        return sp;
    }

    private JButton styledButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(BTN_BG);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
