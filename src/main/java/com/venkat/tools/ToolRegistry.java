package com.venkat.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool t : toolList) {
            tools.put(t.name(), t);
        }
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<Map<String, Object>> getToolDefinitions() {
        return tools.values().stream().map(t -> Map.of("type", "function", "function",
                Map.of("name", t.name(), "description", t.description(), "parameters", t.schema()))).toList();
    }
}
