package com.venkat.tools;

import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Component
public class GetTimeTool implements Tool {
    @Override
    public String name() {
        return "getTime";
    }

    @Override
    public String description() {
        return "Returns current server time in ISO format.";
    }

    @Override
    public String execute(Map<String, Object> args) {
        return "{\"now\":\"" + ZonedDateTime.now().toString() + "\"}";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }
}
