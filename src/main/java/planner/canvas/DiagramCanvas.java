package planner.canvas;

import com.fasterxml.jackson.databind.ObjectMapper;
import planner.ai.DiagramData;
import planner.ui.NodeEditorDialog;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class DiagramCanvas extends JPanel {

    // ── Color palette ──────────────────────────────────────────────────────────
    static final Color BG           = new Color(22,  22,  22);
    static final Color NODE_FILL    = Color.WHITE;
    static final Color NODE_HEADER  = new Color(30,  30,  30);
    static final Color NODE_BORDER  = new Color(180, 180, 180);
    static final Color CONN_COLOR   = new Color(130, 130, 130);
    static final Color SELECT_COLOR = new Color(220, 220, 220);
    static final Color TEXT_HEADER  = Color.WHITE;
    static final Color TEXT_BODY    = Color.BLACK;

    // ── Diagram state ──────────────────────────────────────────────────────────
    private final List<ObjectNode>  nodes       = new ArrayList<>();
    private final List<Connection>  connections = new ArrayList<>();
    private final ArrayDeque<String> undoStack  = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack  = new ArrayDeque<>();
    private static final int MAX_UNDO = 50;

    // ── View transform (zoom + pan) ────────────────────────────────────────────
    private double scale = 1.0;
    private int    panX  = 0;
    private int    panY  = 0;

    // ── Mouse state machine ────────────────────────────────────────────────────
    private enum MouseMode { IDLE, DRAGGING_NODE, DRAWING_CONNECTION, PANNING }
    private MouseMode   mouseMode         = MouseMode.IDLE;
    private ObjectNode  draggedNode;
    private Point       dragOffset;
    private ObjectNode  connectionSource;
    private Point       connectionDragEnd;
    private ObjectNode  selectedNode;
    private boolean     dragMoved         = false; // true once the node actually moves

    private final ObjectMapper mapper = new ObjectMapper();

    // ── Constructor ────────────────────────────────────────────────────────────
    public DiagramCanvas() {
        setBackground(BG);
        setPreferredSize(new Dimension(900, 700));
        UIManager.put("MenuItem.selectionBackground", Color.WHITE);
        UIManager.put("MenuItem.selectionForeground", Color.BLACK);
        UIManager.put("MenuItem.background",          new Color(40, 40, 40));
        UIManager.put("MenuItem.foreground",          Color.WHITE);
        UIManager.put("PopupMenu.background",         new Color(40, 40, 40));

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { onPressed(e); }
            @Override public void mouseReleased(MouseEvent e) { onReleased(e); }
            @Override public void mouseClicked(MouseEvent e)  { onClicked(e); }
        };
        MouseMotionAdapter mma = new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) { onDragged(e); }
        };
        addMouseListener(ma);
        addMouseMotionListener(mma);

        // Zoom centered on the cursor position
        addMouseWheelListener(e -> {
            double factor = e.getPreciseWheelRotation() < 0 ? 1.03 : 1.0 / 1.03;
            double newScale = Math.max(0.15, Math.min(4.0, scale * factor));
            panX = (int)(e.getX() - (e.getX() - panX) * newScale / scale);
            panY = (int)(e.getY() - (e.getY() - panY) * newScale / scale);
            scale = newScale;
            repaint();
        });
    }

    // ── Coordinate helpers ─────────────────────────────────────────────────────
    private int toWorldX(int sx) { return (int)((sx - panX) / scale); }
    private int toWorldY(int sy) { return (int)((sy - panY) / scale); }

    // ── Painting ───────────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Apply view transform
        g2.translate(panX, panY);
        g2.scale(scale, scale);

        // Draw connections first (behind nodes)
        for (Connection c : connections) c.draw(g2);

        // Live connection preview while shift-dragging
        if (mouseMode == MouseMode.DRAWING_CONNECTION && connectionDragEnd != null && connectionSource != null) {
            Point src = connectionSource.borderPoint(connectionDragEnd.x, connectionDragEnd.y);
            g2.setColor(new Color(150, 150, 150, 160));
            float[] dash = {6f, 4f};
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g2.drawLine(src.x, src.y, connectionDragEnd.x, connectionDragEnd.y);
            g2.setStroke(new BasicStroke(1f));
        }

        // Collect which members are anchored by connections (both ends)
        for (ObjectNode n : nodes) n.highlightedMembers.clear();
        for (Connection c : connections) {
            if (c.toAnchorMember   != null && c.to   != null) c.to.highlightedMembers.add(c.toAnchorMember);
            if (c.fromAnchorMember != null && c.from != null) c.from.highlightedMembers.add(c.fromAnchorMember);
        }

        // Draw nodes (layout first so sizes are correct)
        for (ObjectNode n : nodes) {
            n.layout(g2);
            n.draw(g2);
        }
    }

    // ── Mouse handlers ─────────────────────────────────────────────────────────
    private void onPressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
            showContextMenu(e.getX(), e.getY(), e.getComponent());
            return;
        }
        int wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
        ObjectNode hit = nodeAt(wx, wy);
        if (e.isShiftDown() && hit != null) {
            mouseMode         = MouseMode.DRAWING_CONNECTION;
            connectionSource  = hit;
            connectionDragEnd = new Point(wx, wy);
        } else if (hit != null) {
            mouseMode   = MouseMode.DRAGGING_NODE;
            draggedNode = hit;
            dragOffset  = new Point(wx - hit.x, wy - hit.y);
            dragMoved   = false;
            setSelected(hit);
        } else {
            // Left-click on empty canvas: deselect and start panning
            setSelected(null);
            mouseMode  = MouseMode.PANNING;
            dragOffset = new Point(e.getX() - panX, e.getY() - panY);
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
    }

    private void onDragged(MouseEvent e) {
        if (mouseMode == MouseMode.PANNING) {
            panX = e.getX() - dragOffset.x;
            panY = e.getY() - dragOffset.y;
            repaint();
        } else if (mouseMode == MouseMode.DRAGGING_NODE && draggedNode != null) {
            if (!dragMoved) { pushUndo(); dragMoved = true; }
            int wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
            draggedNode.x = wx - dragOffset.x;
            draggedNode.y = wy - dragOffset.y;
            repaint();
        } else if (mouseMode == MouseMode.DRAWING_CONNECTION) {
            connectionDragEnd = new Point(toWorldX(e.getX()), toWorldY(e.getY()));
            repaint();
        }
    }

    private void onReleased(MouseEvent e) {
        if (mouseMode == MouseMode.PANNING) {
            mouseMode = MouseMode.IDLE;
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        if (mouseMode == MouseMode.DRAWING_CONNECTION) {
            int wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
            ObjectNode target = nodeAt(wx, wy);
            if (target != null && target != connectionSource) {
                boolean dup = connections.stream()
                        .anyMatch(c -> (c.fromId.equals(connectionSource.id) && c.toId.equals(target.id))
                                    || (c.fromId.equals(target.id) && c.toId.equals(connectionSource.id)));
                if (!dup) {
                    Connection conn = new Connection();
                    conn.fromId = connectionSource.id;
                    conn.toId   = target.id;
                    conn.label  = "";
                    conn.from   = connectionSource;
                    conn.to     = target;
                    connections.add(conn);
                    showAnchorThenLabelMenu(conn, e.getX(), e.getY());
                }
            }
            connectionDragEnd = null;
            connectionSource  = null;
        }
        mouseMode   = MouseMode.IDLE;
        draggedNode = null;
        repaint();
    }

    private void onClicked(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) return;
        int wx = toWorldX(e.getX()), wy = toWorldY(e.getY());

        // Single click: check for section header toggles
        if (e.getClickCount() == 1) {
            for (ObjectNode n : nodes) {
                if (n.toggleAt(wx, wy)) { repaint(); return; }
            }
        }

        if (e.getClickCount() != 2) return;
        ObjectNode hit = nodeAt(wx, wy);
        if (hit != null) {
            pushUndo();
            NodeEditorDialog dlg = new NodeEditorDialog(
                    SwingUtilities.getWindowAncestor(this), hit);
            dlg.setVisible(true);
            repaint();
        } else {
            pushUndo();
            ObjectNode n = new ObjectNode();
            n.id        = "node-" + UUID.randomUUID().toString().substring(0, 8);
            n.className = "NewClass";
            n.x = wx - 85;
            n.y = wy - 20;
            nodes.add(n);
            NodeEditorDialog dlg = new NodeEditorDialog(
                    SwingUtilities.getWindowAncestor(this), n);
            dlg.setVisible(true);
            repaint();
        }
    }

    private void showContextMenu(int screenX, int screenY, Component invoker) {
        int wx = toWorldX(screenX), wy = toWorldY(screenY);
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(40, 40, 40));

        ObjectNode hit     = nodeAt(wx, wy);
        Connection hitConn = connectionAt(wx, wy);

        if (hit != null) {
            JMenuItem edit = styledItem("Edit Node");
            edit.addActionListener(e -> {
                pushUndo();
                NodeEditorDialog dlg = new NodeEditorDialog(
                        SwingUtilities.getWindowAncestor(this), hit);
                dlg.setVisible(true);
                repaint();
            });
            menu.add(edit);

            JMenuItem del = styledItem("Delete Node");
            del.addActionListener(e -> {
                pushUndo();
                nodes.remove(hit);
                connections.removeIf(c -> c.fromId.equals(hit.id) || c.toId.equals(hit.id));
                repaint();
            });
            menu.add(del);
        } else if (hitConn != null) {
            JMenuItem editLabel = styledItem("Edit Label");
            editLabel.addActionListener(e -> {
                String lbl = JOptionPane.showInputDialog(this, "Connection label:", hitConn.label);
                if (lbl != null) { pushUndo(); hitConn.label = lbl; repaint(); }
            });
            menu.add(editLabel);

            JMenuItem editToAnchor = styledItem("Edit Target Anchor");
            editToAnchor.addActionListener(e -> showToAnchorMenu(hitConn, screenX, screenY));
            menu.add(editToAnchor);

            JMenuItem editFromAnchor = styledItem("Edit Source Anchor");
            editFromAnchor.addActionListener(e -> showFromAnchorMenu(hitConn, screenX, screenY));
            menu.add(editFromAnchor);

            JMenuItem delConn = styledItem("Delete Connection");
            delConn.addActionListener(e -> { pushUndo(); connections.remove(hitConn); repaint(); });
            menu.add(delConn);
        } else {
            JMenuItem clearConns = styledItem("Clear All Connections");
            clearConns.addActionListener(e -> { pushUndo(); connections.clear(); repaint(); });
            menu.add(clearConns);
        }

        menu.show(invoker, screenX, screenY);
    }

    /** Step 1 (new connection): pick source anchor member, then proceed to target anchor. */
    private void showAnchorThenLabelMenu(Connection conn, int screenX, int screenY) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(40, 40, 40));

        JMenuItem classItem = styledItem("← " + conn.from.className + " (whole class)");
        classItem.addActionListener(e -> showTargetAnchorThenLabel(conn, screenX, screenY));
        menu.add(classItem);

        if (!conn.from.fields.isEmpty()) {
            menu.addSeparator();
            for (String f : conn.from.fields) {
                JMenuItem fi = styledItem("  " + f);
                fi.addActionListener(e -> { conn.fromAnchorMember = f; showTargetAnchorThenLabel(conn, screenX, screenY); });
                menu.add(fi);
            }
        }
        if (!conn.from.methods.isEmpty()) {
            menu.addSeparator();
            for (String m : conn.from.methods) {
                JMenuItem mi = styledItem("  " + m);
                mi.addActionListener(e -> { conn.fromAnchorMember = m; showTargetAnchorThenLabel(conn, screenX, screenY); });
                menu.add(mi);
            }
        }

        menu.show(this, screenX, screenY);
    }

    /** Step 2 (new connection): pick target anchor member, then pick label. */
    private void showTargetAnchorThenLabel(Connection conn, int screenX, int screenY) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(40, 40, 40));

        JMenuItem classItem = styledItem("→ " + conn.to.className + " (whole class)");
        classItem.addActionListener(e -> showConnectionTypeMenu(conn, screenX, screenY));
        menu.add(classItem);

        if (!conn.to.fields.isEmpty()) {
            menu.addSeparator();
            for (String f : conn.to.fields) {
                JMenuItem fi = styledItem("  " + f);
                fi.addActionListener(e -> { conn.toAnchorMember = f; showConnectionTypeMenu(conn, screenX, screenY); });
                menu.add(fi);
            }
        }
        if (!conn.to.methods.isEmpty()) {
            menu.addSeparator();
            for (String m : conn.to.methods) {
                JMenuItem mi = styledItem("  " + m);
                mi.addActionListener(e -> { conn.toAnchorMember = m; showConnectionTypeMenu(conn, screenX, screenY); });
                menu.add(mi);
            }
        }

        menu.show(this, screenX, screenY);
    }

    /** Stand-alone target anchor editor (right-click on existing connection). */
    private void showToAnchorMenu(Connection conn, int screenX, int screenY) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(40, 40, 40));

        JMenuItem classItem = styledItem("→ " + conn.to.className + " (whole class)");
        classItem.addActionListener(e -> { pushUndo(); conn.toAnchorMember = null; repaint(); });
        menu.add(classItem);

        if (!conn.to.fields.isEmpty()) {
            menu.addSeparator();
            for (String f : conn.to.fields) {
                JMenuItem fi = styledItem("  " + f);
                fi.addActionListener(e -> { pushUndo(); conn.toAnchorMember = f; repaint(); });
                menu.add(fi);
            }
        }
        if (!conn.to.methods.isEmpty()) {
            menu.addSeparator();
            for (String m : conn.to.methods) {
                JMenuItem mi = styledItem("  " + m);
                mi.addActionListener(e -> { pushUndo(); conn.toAnchorMember = m; repaint(); });
                menu.add(mi);
            }
        }

        menu.show(this, screenX, screenY);
    }

    /** Stand-alone source anchor editor (right-click on existing connection). */
    private void showFromAnchorMenu(Connection conn, int screenX, int screenY) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(40, 40, 40));

        JMenuItem classItem = styledItem("← " + conn.from.className + " (whole class)");
        classItem.addActionListener(e -> { pushUndo(); conn.fromAnchorMember = null; repaint(); });
        menu.add(classItem);

        if (!conn.from.fields.isEmpty()) {
            menu.addSeparator();
            for (String f : conn.from.fields) {
                JMenuItem fi = styledItem("  " + f);
                fi.addActionListener(e -> { pushUndo(); conn.fromAnchorMember = f; repaint(); });
                menu.add(fi);
            }
        }
        if (!conn.from.methods.isEmpty()) {
            menu.addSeparator();
            for (String m : conn.from.methods) {
                JMenuItem mi = styledItem("  " + m);
                mi.addActionListener(e -> { pushUndo(); conn.fromAnchorMember = m; repaint(); });
                menu.add(mi);
            }
        }

        menu.show(this, screenX, screenY);
    }

    /** Step 3 (new connection): pick the relationship label. */
    private void showConnectionTypeMenu(Connection conn, int screenX, int screenY) {
        String[] types = {"extends", "implements", "uses", "has", "creates"};
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(40, 40, 40));
        for (String t : types) {
            JMenuItem item = styledItem(t);
            item.addActionListener(e -> { conn.label = t; repaint(); });
            menu.add(item);
        }
        menu.show(this, screenX, screenY);
    }

    private JMenuItem styledItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setUI(new javax.swing.plaf.basic.BasicMenuItemUI());
        item.setBackground(new Color(40, 40, 40));
        item.setForeground(Color.WHITE);
        item.setOpaque(true);
        item.setBorderPainted(false);
        return item;
    }

    // ── Hit testing ────────────────────────────────────────────────────────────
    private ObjectNode nodeAt(int x, int y) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            if (nodes.get(i).contains(x, y)) return nodes.get(i);
        }
        return null;
    }

    private Connection connectionAt(int x, int y) {
        for (Connection c : connections) {
            if (c.from == null || c.to == null) continue;
            int cx1 = c.from.x + c.from.width  / 2;
            int cy1 = c.from.y + c.from.height / 2;
            int cx2 = c.to.x   + c.to.width    / 2;
            int cy2 = c.to.y   + c.to.height   / 2;
            double len2 = (double)(cx2-cx1)*(cx2-cx1) + (double)(cy2-cy1)*(cy2-cy1);
            if (len2 == 0) continue;
            double t = Math.max(0, Math.min(1,
                    ((x-cx1)*(cx2-cx1) + (y-cy1)*(cy2-cy1)) / len2));
            double dist = Math.hypot(x - (cx1 + t*(cx2-cx1)), y - (cy1 + t*(cy2-cy1)));
            if (dist < 6) return c;
        }
        return null;
    }

    private void setSelected(ObjectNode n) {
        if (selectedNode != null) selectedNode.selected = false;
        selectedNode = n;
        if (n != null) n.selected = true;
    }

    // ── Undo ───────────────────────────────────────────────────────────────────
    public void pushUndo() {
        try {
            String snap = mapper.writeValueAsString(getDiagramData());
            undoStack.push(snap);
            if (undoStack.size() > MAX_UNDO) undoStack.pollLast();
            redoStack.clear(); // new action invalidates redo history
        } catch (Exception ignored) {}
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        try {
            redoStack.push(mapper.writeValueAsString(getDiagramData()));
        } catch (Exception ignored) {}
        restoreSnapshot(undoStack.pop());
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        try {
            undoStack.push(mapper.writeValueAsString(getDiagramData()));
        } catch (Exception ignored) {}
        restoreSnapshot(redoStack.pop());
    }

    private void restoreSnapshot(String snap) {
        try {
            DiagramData data = mapper.readValue(snap, DiagramData.class);
            nodes.clear();
            connections.clear();
            selectedNode = null;
            Map<String, ObjectNode> nodeMap = new HashMap<>();
            if (data.classes != null) {
                for (DiagramData.ClassNode cn : data.classes) {
                    ObjectNode n = new ObjectNode();
                    n.id               = cn.id;
                    n.className        = cn.className;
                    n.fields           = cn.fields  != null ? cn.fields  : new ArrayList<>();
                    n.methods          = cn.methods != null ? cn.methods : new ArrayList<>();
                    n.x                = cn.x;
                    n.y                = cn.y;
                    n.fieldsCollapsed  = cn.fieldsCollapsed;
                    n.methodsCollapsed = cn.methodsCollapsed;
                    nodes.add(n);
                    nodeMap.put(n.id, n);
                }
            }
            if (data.connections != null) {
                for (DiagramData.ConnectionData cd : data.connections) {
                    if (cd.fromId == null || cd.toId == null) continue;
                    if (!nodeMap.containsKey(cd.fromId) || !nodeMap.containsKey(cd.toId)) continue;
                    Connection c = new Connection();
                    c.fromId           = cd.fromId;
                    c.toId             = cd.toId;
                    c.label            = cd.label           != null ? cd.label : "";
                    c.toAnchorMember   = cd.toAnchorMember;
                    c.fromAnchorMember = cd.fromAnchorMember;
                    c.from             = nodeMap.get(cd.fromId);
                    c.to               = nodeMap.get(cd.toId);
                    connections.add(c);
                }
            }
            repaint();
        } catch (Exception ignored) {}
    }

    // ── Public API ─────────────────────────────────────────────────────────────
    public void clearAll() {
        pushUndo();
        nodes.clear();
        connections.clear();
        selectedNode = null;
        repaint();
    }

    public void newDiagram() { clearAll(); }

    public void applyDiagram(DiagramData data, boolean replace) {
        pushUndo();

        // Snapshot existing positions by className before any clearing
        Map<String, Point> prevPos = new HashMap<>();
        for (ObjectNode n : nodes) prevPos.put(n.className, new Point(n.x, n.y));

        if (replace) {
            nodes.clear();
            connections.clear();
            selectedNode = null;
        }

        Set<String> existingIds   = new HashSet<>();
        Set<String> existingNames = new HashSet<>();
        for (ObjectNode n : nodes) {
            existingIds.add(n.id);
            existingNames.add(n.className);
        }

        Map<String, ObjectNode> nodeMap = new HashMap<>();
        for (ObjectNode n : nodes) nodeMap.put(n.id, n);

        Map<String, String> idRemap = new HashMap<>();

        List<ObjectNode> newNodes = new ArrayList<>();
        if (data.classes != null) {
            for (DiagramData.ClassNode cn : data.classes) {
                if (cn.className == null) continue;
                if (existingNames.contains(cn.className)) continue;

                ObjectNode n = new ObjectNode();
                n.className        = cn.className;
                n.fields           = cn.fields  != null ? cn.fields  : new ArrayList<>();
                n.methods          = cn.methods != null ? cn.methods : new ArrayList<>();
                n.fieldsCollapsed  = cn.fieldsCollapsed;
                n.methodsCollapsed = cn.methodsCollapsed;

                String origId = cn.id != null ? cn.id : cn.className;
                if (existingIds.contains(origId)) {
                    String newId = origId + "-" + UUID.randomUUID().toString().substring(0, 6);
                    idRemap.put(origId, newId);
                    n.id = newId;
                } else {
                    n.id = origId;
                }

                existingIds.add(n.id);
                nodeMap.put(n.id, n);
                newNodes.add(n);
            }
        }

        // Restore previous positions; collect only truly new nodes for layout
        List<ObjectNode> needsLayout = new ArrayList<>();
        for (ObjectNode n : newNodes) {
            Point p = prevPos.get(n.className);
            if (p != null) { n.x = p.x; n.y = p.y; }
            else            needsLayout.add(n);
        }

        // Seed new nodes in a wide grid so force simulation starts spread out
        int startX = 60;
        for (ObjectNode n : nodes) startX = Math.max(startX, n.x + 440);
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(needsLayout.size())));
        for (int i = 0; i < needsLayout.size(); i++) {
            needsLayout.get(i).x = startX + (i % cols) * 420;
            needsLayout.get(i).y = 60     + (i / cols) * 340;
        }

        nodes.addAll(newNodes);

        // Build connection pairs from incoming data for the spring layout
        List<ObjectNode[]> connPairs = new ArrayList<>();
        if (data.connections != null) {
            for (DiagramData.ConnectionData cd : data.connections) {
                if (cd.fromId == null || cd.toId == null) continue;
                String fId = idRemap.getOrDefault(cd.fromId, cd.fromId);
                String tId = idRemap.getOrDefault(cd.toId,   cd.toId);
                ObjectNode from = nodeMap.get(fId), to = nodeMap.get(tId);
                if (from != null && to != null) connPairs.add(new ObjectNode[]{from, to});
            }
        }

        forceLayout(needsLayout, nodes, connPairs);

        // Add connection objects
        if (data.connections != null) {
            for (DiagramData.ConnectionData cd : data.connections) {
                if (cd.fromId == null || cd.toId == null) continue;
                String fId = idRemap.getOrDefault(cd.fromId, cd.fromId);
                String tId = idRemap.getOrDefault(cd.toId,   cd.toId);
                if (!nodeMap.containsKey(fId) || !nodeMap.containsKey(tId)) continue;
                boolean dup = connections.stream()
                        .anyMatch(c -> c.fromId.equals(fId) && c.toId.equals(tId));
                if (dup) continue;
                Connection c = new Connection();
                c.fromId           = fId;
                c.toId             = tId;
                c.label            = cd.label           != null ? cd.label : "";
                c.toAnchorMember   = cd.toAnchorMember;
                c.fromAnchorMember = cd.fromAnchorMember;
                c.from             = nodeMap.get(fId);
                c.to               = nodeMap.get(tId);
                connections.add(c);
            }
        }

        repaint();
    }

    /**
     * Spring-based force layout. Only nodes in {@code toPlace} are moved;
     * all nodes in {@code allNodes} participate in repulsion so existing
     * positioned nodes push new ones away.
     */
    private void forceLayout(List<ObjectNode> toPlace, List<ObjectNode> allNodes,
                             List<ObjectNode[]> connPairs) {
        if (toPlace.isEmpty()) return;
        int n = toPlace.size();
        double[] fx = new double[n];
        double[] fy = new double[n];
        // k = ideal clearance between node centres (~node width + comfortable gap)
        double k = 420;

        for (int iter = 0; iter < 400; iter++) {
            Arrays.fill(fx, 0);
            Arrays.fill(fy, 0);
            double temp = k * Math.max(0.04, 1.0 - iter / 300.0);

            // Repulsion: each movable node repelled by every other node
            for (int i = 0; i < n; i++) {
                ObjectNode ni = toPlace.get(i);
                for (ObjectNode nj : allNodes) {
                    if (nj == ni) continue;
                    double dx = ni.x - nj.x, dy = ni.y - nj.y;
                    // Enforce a minimum separation so nodes never completely overlap
                    double dist = Math.max(30, Math.sqrt(dx * dx + dy * dy));
                    double rep  = k * k / dist;
                    fx[i] += rep * dx / dist;
                    fy[i] += rep * dy / dist;
                }
            }

            // Attraction: pull connected nodes together (weaker than repulsion)
            for (ObjectNode[] pair : connPairs) {
                int ai = toPlace.indexOf(pair[0]);
                int bi = toPlace.indexOf(pair[1]);
                if (ai < 0 && bi < 0) continue;
                double dx = pair[1].x - pair[0].x, dy = pair[1].y - pair[0].y;
                double dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
                double att  = (dist * dist / k) * 0.5; // 0.5 dampens attraction vs repulsion
                if (ai >= 0) { fx[ai] += att * dx / dist; fy[ai] += att * dy / dist; }
                if (bi >= 0) { fx[bi] -= att * dx / dist; fy[bi] -= att * dy / dist; }
            }

            // Apply forces, clamped by cooling temperature
            for (int i = 0; i < n; i++) {
                double len = Math.sqrt(fx[i] * fx[i] + fy[i] * fy[i]);
                if (len > 0) {
                    double move = Math.min(len, temp);
                    toPlace.get(i).x += (int)(fx[i] / len * move);
                    toPlace.get(i).y  = Math.max(10, toPlace.get(i).y + (int)(fy[i] / len * move));
                }
            }
        }
    }

    public DiagramData getDiagramData() {
        DiagramData data = new DiagramData();
        for (ObjectNode n : nodes) {
            DiagramData.ClassNode cn = new DiagramData.ClassNode();
            cn.id              = n.id;
            cn.className       = n.className;
            cn.fields          = new ArrayList<>(n.fields);
            cn.methods         = new ArrayList<>(n.methods);
            cn.x               = n.x;
            cn.y               = n.y;
            cn.fieldsCollapsed  = n.fieldsCollapsed;
            cn.methodsCollapsed = n.methodsCollapsed;
            data.classes.add(cn);
        }
        for (Connection c : connections) {
            DiagramData.ConnectionData cd = new DiagramData.ConnectionData();
            cd.fromId           = c.fromId;
            cd.toId             = c.toId;
            cd.label            = c.label;
            cd.toAnchorMember   = c.toAnchorMember;
            cd.fromAnchorMember = c.fromAnchorMember;
            data.connections.add(cd);
        }
        return data;
    }

    // ── Save / Load ────────────────────────────────────────────────────────────
    private static final File SAVES_DIR = new File(
            System.getProperty("user.home") + "/Documents/JavaPlanner/saves");

    private JFileChooser savesDirChooser() {
        SAVES_DIR.mkdirs();
        JFileChooser fc = new JFileChooser(SAVES_DIR);
        fc.setFileFilter(new FileNameExtensionFilter("JSON Diagram (*.json)", "json"));
        fc.setFileHidingEnabled(true);
        // Lock navigation to the saves directory
        fc.setFileSystemView(new javax.swing.filechooser.FileSystemView() {
            @Override public File createNewFolder(File f) throws java.io.IOException {
                throw new java.io.IOException("Not allowed");
            }
            @Override public File getDefaultDirectory()  { return SAVES_DIR; }
            @Override public File getHomeDirectory()     { return SAVES_DIR; }
            @Override public File[] getRoots()           { return new File[]{SAVES_DIR}; }
        });
        return fc;
    }

    public void saveDiagram() {
        JFileChooser fc = savesDirChooser();
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().endsWith(".json")) f = new File(f.getPath() + ".json");
        // Ensure it stays inside saves dir
        if (!f.getParentFile().getAbsolutePath().equals(SAVES_DIR.getAbsolutePath()))
            f = new File(SAVES_DIR, f.getName());
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(f, getDiagramData());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
        }
    }

    public void loadDiagram() {
        JFileChooser fc = savesDirChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            DiagramData data = mapper.readValue(fc.getSelectedFile(), DiagramData.class);
            pushUndo();
            nodes.clear();
            connections.clear();
            selectedNode = null;
            Map<String, ObjectNode> nodeMap = new HashMap<>();
            if (data.classes != null) {
                for (DiagramData.ClassNode cn : data.classes) {
                    ObjectNode n = new ObjectNode();
                    n.id               = cn.id;
                    n.className        = cn.className;
                    n.fields           = cn.fields  != null ? cn.fields  : new ArrayList<>();
                    n.methods          = cn.methods != null ? cn.methods : new ArrayList<>();
                    n.x                = cn.x;
                    n.y                = cn.y;
                    n.fieldsCollapsed  = cn.fieldsCollapsed;
                    n.methodsCollapsed = cn.methodsCollapsed;
                    nodes.add(n);
                    nodeMap.put(n.id, n);
                }
            }
            if (data.connections != null) {
                for (DiagramData.ConnectionData cd : data.connections) {
                    if (cd.fromId == null || cd.toId == null) continue;
                    if (!nodeMap.containsKey(cd.fromId) || !nodeMap.containsKey(cd.toId)) continue;
                    Connection c = new Connection();
                    c.fromId           = cd.fromId;
                    c.toId             = cd.toId;
                    c.label            = cd.label           != null ? cd.label : "";
                    c.toAnchorMember   = cd.toAnchorMember;
                    c.fromAnchorMember = cd.fromAnchorMember;
                    c.from             = nodeMap.get(cd.fromId);
                    c.to               = nodeMap.get(cd.toId);
                    connections.add(c);
                }
            }
            repaint();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Load failed: " + ex.getMessage());
        }
    }
}
