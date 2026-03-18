package planner.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DiagramData {

    public List<ClassNode> classes = new ArrayList<>();
    public List<ConnectionData> connections = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClassNode {
        public String id;
        public String className;
        public List<String> fields = new ArrayList<>();
        public List<String> methods = new ArrayList<>();
        public int x;
        public int y;
        public boolean fieldsCollapsed;
        public boolean methodsCollapsed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConnectionData {
        public String fromId;
        public String toId;
        public String label;
        public String toAnchorMember;
        public String fromAnchorMember;
    }
}
