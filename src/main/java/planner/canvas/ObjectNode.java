package planner.canvas;

import java.awt.*;
import java.util.*;
import java.util.List;

public class ObjectNode {

    public String id;
    public String className;
    public List<String> fields  = new ArrayList<>();
    public List<String> methods = new ArrayList<>();
    public int x, y;
    public boolean fieldsCollapsed  = false;
    public boolean methodsCollapsed = false;

    public transient int     width  = 170;
    public transient int     height = 80;
    public transient boolean selected;
    public transient int     fieldsSectionY;
    public transient int     methodsSectionY;

    /** Maps each field/method string to the Y center of its rendered row (world coords). */
    public transient Map<String, Integer> memberRowCenterY  = new LinkedHashMap<>();
    /** Members currently targeted by an anchored connection — highlighted on draw. */
    public transient Set<String>          highlightedMembers = new HashSet<>();

    private static final int  HEADER_H    = 26;
    private static final int  SECTION_H   = 17;
    private static final int  LINE_PAD    = 3;
    private static final Font HEADER_FONT  = new Font("Monospaced", Font.BOLD,  12);
    private static final Font BODY_FONT    = new Font("Monospaced", Font.PLAIN, 11);
    private static final Font SECTION_FONT = new Font("SansSerif",  Font.BOLD,  10);

    private static final Color SECTION_BG   = new Color(230, 230, 230);
    private static final Color SECTION_FG   = new Color(80,  80,  80);

    public void layout(Graphics2D g2) {
        FontMetrics fmHeader = g2.getFontMetrics(HEADER_FONT);
        FontMetrics fmBody   = g2.getFontMetrics(BODY_FONT);
        int lineH = fmBody.getHeight() + LINE_PAD;

        int minW = fmHeader.stringWidth(className) + 24;
        for (String f : fields)  minW = Math.max(minW, fmBody.stringWidth(f) + 16);
        for (String m : methods) minW = Math.max(minW, fmBody.stringWidth(m) + 16);
        width = Math.max(170, minW);

        // Section Y positions
        fieldsSectionY  = y + HEADER_H;
        int fieldRows   = fieldsCollapsed  ? 0 : fields.size();
        methodsSectionY = fieldsSectionY + SECTION_H + fieldRows * lineH;
        int methodRows  = methodsCollapsed ? 0 : methods.size();
        height          = (methodsSectionY - y) + SECTION_H + methodRows * lineH + 6;

        // Record member row centers for connection anchoring (only visible rows)
        memberRowCenterY.clear();
        if (!fieldsCollapsed) {
            int fy = fieldsSectionY + SECTION_H;
            for (String f : fields) {
                memberRowCenterY.put(f, fy + lineH / 2);
                fy += lineH;
            }
        }
        if (!methodsCollapsed) {
            int my = methodsSectionY + SECTION_H;
            for (String m : methods) {
                memberRowCenterY.put(m, my + lineH / 2);
                my += lineH;
            }
        }
    }

    public void draw(Graphics2D g2) {
        FontMetrics fmBody = g2.getFontMetrics(BODY_FONT);
        int lineH = fmBody.getHeight() + LINE_PAD;

        // Shadow
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillRoundRect(x + 3, y + 3, width, height, 4, 4);

        // Node background
        g2.setColor(DiagramCanvas.NODE_FILL);
        g2.fillRect(x, y, width, height);

        // Header bar
        g2.setColor(DiagramCanvas.NODE_HEADER);
        g2.fillRect(x, y, width, HEADER_H);

        // Class name in header
        g2.setFont(HEADER_FONT);
        g2.setColor(DiagramCanvas.TEXT_HEADER);
        FontMetrics fmH = g2.getFontMetrics();
        int nameX = x + (width - fmH.stringWidth(className)) / 2;
        g2.drawString(className, nameX, y + HEADER_H - 7);

        // ── Fields section ──────────────────────────────────────────────────
        drawSectionHeader(g2, "fields", fieldsSectionY, fieldsCollapsed);

        if (!fieldsCollapsed) {
            int fy = fieldsSectionY + SECTION_H + fmBody.getAscent() + 2;
            for (String f : fields) {
                if (highlightedMembers.contains(f)) {
                    g2.setColor(new Color(255, 200, 60, 90));
                    g2.fillRect(x + 1, fy - fmBody.getAscent(), width - 2, lineH);
                }
                g2.setFont(BODY_FONT);
                g2.setColor(DiagramCanvas.TEXT_BODY);
                g2.drawString(f, x + 8, fy);
                fy += lineH;
            }
        }

        // ── Methods section ─────────────────────────────────────────────────
        drawSectionHeader(g2, "methods", methodsSectionY, methodsCollapsed);

        if (!methodsCollapsed) {
            int my = methodsSectionY + SECTION_H + fmBody.getAscent() + 2;
            for (String m : methods) {
                if (highlightedMembers.contains(m)) {
                    g2.setColor(new Color(255, 200, 60, 90));
                    g2.fillRect(x + 1, my - fmBody.getAscent(), width - 2, lineH);
                }
                g2.setFont(BODY_FONT);
                g2.setColor(DiagramCanvas.TEXT_BODY);
                g2.drawString(m, x + 8, my);
                my += lineH;
            }
        }

        // Border
        g2.setColor(selected ? DiagramCanvas.SELECT_COLOR : DiagramCanvas.NODE_BORDER);
        g2.setStroke(new BasicStroke(selected ? 2f : 1f));
        g2.drawRect(x, y, width, height);
        g2.setStroke(new BasicStroke(1f));
    }

    private void drawSectionHeader(Graphics2D g2, String label, int sy, boolean collapsed) {
        g2.setColor(SECTION_BG);
        g2.fillRect(x + 1, sy, width - 2, SECTION_H);

        g2.setColor(new Color(200, 200, 200));
        g2.drawLine(x, sy, x + width, sy);

        g2.setFont(SECTION_FONT);
        g2.setColor(SECTION_FG);
        String arrow = collapsed ? "▶" : "▼";
        g2.drawString(arrow + " " + label, x + 6, sy + SECTION_H - 4);
    }

    /** Returns true (and toggles) if (px,py) lands on a section toggle header. */
    public boolean toggleAt(int px, int py) {
        if (px < x || px > x + width) return false;
        if (py >= fieldsSectionY && py < fieldsSectionY + SECTION_H) {
            fieldsCollapsed = !fieldsCollapsed;
            return true;
        }
        if (py >= methodsSectionY && py < methodsSectionY + SECTION_H) {
            methodsCollapsed = !methodsCollapsed;
            return true;
        }
        return false;
    }

    public boolean contains(int px, int py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /** Returns the point on this node's border closest to the external point (ex, ey). */
    public Point borderPoint(int ex, int ey) {
        int cx = x + width  / 2;
        int cy = y + height / 2;
        if (ex == cx && ey == cy) return new Point(cx, cy);

        double dx = ex - cx, dy = ey - cy;
        double tBest = Double.MAX_VALUE;
        int rx = cx, ry = cy;

        if (dy != 0) {
            double t = (y - cy) / dy;
            double ix = cx + t * dx;
            if (t > 0 && ix >= x && ix <= x + width && t < tBest) { tBest = t; rx = (int)ix; ry = y; }
        }
        if (dy != 0) {
            double t = (y + height - cy) / dy;
            double ix = cx + t * dx;
            if (t > 0 && ix >= x && ix <= x + width && t < tBest) { tBest = t; rx = (int)ix; ry = y + height; }
        }
        if (dx != 0) {
            double t = (x - cx) / dx;
            double iy = cy + t * dy;
            if (t > 0 && iy >= y && iy <= y + height && t < tBest) { tBest = t; rx = x; ry = (int)iy; }
        }
        if (dx != 0) {
            double t = (x + width - cx) / dx;
            double iy = cy + t * dy;
            if (t > 0 && iy >= y && iy <= y + height && t < tBest) { tBest = t; rx = x + width; ry = (int)iy; }
        }
        return new Point(rx, ry);
    }
}
