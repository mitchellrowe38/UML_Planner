package planner.canvas;

import java.awt.*;
import java.util.Map;

public class Connection {

    public String fromId;
    public String toId;
    public String label;
    public String toAnchorMember;    // null = connect to center of target node
    public String fromAnchorMember;  // null = exit from source border toward target

    public transient ObjectNode from;
    public transient ObjectNode to;

    private static final Font LABEL_FONT = new Font("Monospaced", Font.PLAIN, 10);

    public void resolve(Map<String, ObjectNode> nodeMap) {
        from = nodeMap.get(fromId);
        to   = nodeMap.get(toId);
    }

    /** Returns {src, dst} — the actual visual endpoints of this connection line. */
    public Point[] visualEndpoints() {
        int fromCx = from.x + from.width  / 2;
        int fromCy = from.y + from.height / 2;
        int toCx   = to.x   + to.width    / 2;
        int toCy   = to.y   + to.height   / 2;
        boolean toIsRight = toCx >= fromCx;

        Point src = null, dst = null;
        if (fromAnchorMember != null && from.memberRowCenterY.containsKey(fromAnchorMember)) {
            int anchorY = from.memberRowCenterY.get(fromAnchorMember);
            src = toIsRight ? new Point(from.x + from.width, anchorY) : new Point(from.x, anchorY);
        }
        if (toAnchorMember != null && to.memberRowCenterY.containsKey(toAnchorMember)) {
            int anchorY = to.memberRowCenterY.get(toAnchorMember);
            dst = toIsRight ? new Point(to.x, anchorY) : new Point(to.x + to.width, anchorY);
        }
        if (src == null && dst == null) {
            src = from.borderPoint(toCx, toCy);
            dst = to.borderPoint(fromCx, fromCy);
        } else if (src == null) {
            src = from.borderPoint(dst.x, dst.y);
        } else if (dst == null) {
            dst = to.borderPoint(src.x, src.y);
        }
        return new Point[]{src, dst};
    }

    public void draw(Graphics2D g2) {
        if (from == null || to == null) return;

        Point[] ep = visualEndpoints();
        Point src = ep[0], dst = ep[1];

        g2.setColor(new Color(200, 200, 200));
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(src.x, src.y, dst.x, dst.y);

        // Source dot
        int dotR = 5;
        g2.fillOval(src.x - dotR, src.y - dotR, dotR * 2, dotR * 2);

        // Arrowhead at dst
        double angle  = Math.atan2(dst.y - src.y, dst.x - src.x);
        int arrowLen  = 14;
        double spread = 0.42;
        int x1 = (int)(dst.x - arrowLen * Math.cos(angle - spread));
        int y1 = (int)(dst.y - arrowLen * Math.sin(angle - spread));
        int x2 = (int)(dst.x - arrowLen * Math.cos(angle + spread));
        int y2 = (int)(dst.y - arrowLen * Math.sin(angle + spread));
        g2.fillPolygon(new int[]{dst.x, x1, x2}, new int[]{dst.y, y1, y2}, 3);

        // Label at midpoint
        if (label != null && !label.isBlank()) {
            int mx = (src.x + dst.x) / 2;
            int my = (src.y + dst.y) / 2;
            g2.setFont(LABEL_FONT);
            FontMetrics fm = g2.getFontMetrics();
            int lw = fm.stringWidth(label);
            g2.setColor(new Color(35, 35, 35));
            g2.fillRect(mx - lw / 2 - 3, my - fm.getAscent() - 2, lw + 6, fm.getHeight() + 4);
            g2.setColor(new Color(210, 210, 210));
            g2.drawString(label, mx - lw / 2, my);
        }

        g2.setStroke(new BasicStroke(1f));
    }
}
