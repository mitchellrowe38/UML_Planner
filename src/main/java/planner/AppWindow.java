package planner;

import planner.canvas.DiagramCanvas;
import planner.ui.AIPanel;
import planner.ui.Toolbar;

import javax.swing.*;
import java.awt.*;

public class AppWindow extends JFrame {

    public AppWindow() {
        setTitle("JavaPlanner");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Dark title bar on supported platforms
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        DiagramCanvas canvas = new DiagramCanvas();
        AIPanel       ai     = new AIPanel(canvas);
        canvas.setOnProjectReset(ai::clearChat);
        Toolbar       tb     = new Toolbar(canvas, ai::scanProject);

        add(tb,     BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(ai,     BorderLayout.EAST);
    }
}
