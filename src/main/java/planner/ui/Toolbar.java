package planner.ui;

import planner.canvas.DiagramCanvas;

import javax.swing.*;
import java.awt.*;

public class Toolbar extends JPanel {

    public Toolbar(DiagramCanvas canvas, Runnable scanAction) {
        setLayout(null);
        setPreferredSize(new Dimension(0, 38));
        setBackground(new Color(38, 38, 38));

        String[] labels  = {"New", "Save", "Load", "Clear"};
        Runnable[] actions = {
            canvas::newDiagram,
            canvas::saveDiagram,
            canvas::loadDiagram,
            canvas::clearAll
        };

        int btnW = 90, btnH = 26, x = 8;
        for (int i = 0; i < labels.length; i++) {
            JButton btn = new JButton(labels[i]);
            btn.setBounds(x, 6, btnW, btnH);
            btn.setBackground(new Color(100, 100, 100));
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("SansSerif", Font.BOLD, 13));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final Runnable action = actions[i];
            btn.addActionListener(e -> action.run());
            add(btn);
            x += btnW + 6;
        }

        // Undo / Redo — drawn as painted triangles, no text
        JButton undoBtn = arrowButton(false);
        undoBtn.setBounds(x, 6, 46, btnH);
        undoBtn.setToolTipText("Undo");
        undoBtn.addActionListener(e -> canvas.undo());
        add(undoBtn);
        x += 46 + 4;

        JButton redoBtn = arrowButton(true);
        redoBtn.setBounds(x, 6, 46, btnH);
        redoBtn.setToolTipText("Redo");
        redoBtn.addActionListener(e -> canvas.redo());
        add(redoBtn);
        x += 46 + 6;

        JButton scanBtn = new JButton("Scan Project...");
        scanBtn.setBounds(x, 6, 140, btnH);
        scanBtn.setBackground(new Color(60, 90, 60));
        scanBtn.setForeground(Color.WHITE);
        scanBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        scanBtn.setFocusPainted(false);
        scanBtn.setBorderPainted(false);
        scanBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        scanBtn.addActionListener(e -> scanAction.run());
        add(scanBtn);
        x += 140 + 6;

        // Hint label on right
        JLabel hint = new JLabel("Shift+drag: connect  |  Double-click: create/edit  |  Right-click: menu  |  Scroll: zoom  |  Drag empty space: pan");
        hint.setForeground(new Color(90, 90, 90));
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        hint.setBounds(x + 20, 10, 600, 18);
        add(hint);
    }

    /** Creates a button that paints a filled triangle instead of text. */
    private JButton arrowButton(boolean pointRight) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                // Background
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                // Triangle
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                int cx = getWidth() / 2, cy = getHeight() / 2;
                int half = 7;
                if (pointRight) {
                    int[] xs = {cx - half, cx + half, cx - half};
                    int[] ys = {cy - half, cy,         cy + half};
                    g2.fillPolygon(xs, ys, 3);
                } else {
                    int[] xs = {cx + half, cx - half, cx + half};
                    int[] ys = {cy - half, cy,         cy + half};
                    g2.fillPolygon(xs, ys, 3);
                }
            }
        };
        btn.setBackground(new Color(100, 100, 100));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false); // let paintComponent handle fill
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
