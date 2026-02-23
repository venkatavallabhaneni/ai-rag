package com.venkat.tools;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GetServiceHealthTool implements Tool {

    @Override
    public String name() {
        return "getServiceHealth";
    }

    @Override
    public String description() {
        return "Returns health status of a service";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of("type", "object", "properties",
                Map.of("serviceName", Map.of("type", "string", "description", "Name of service")), "required",
                List.of("serviceName"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        String service = (String) args.get("serviceName");

        // Fake backend logic
        if ("billing".equalsIgnoreCase(service)) {
            return "Service billing is UP. Latency 120ms.";
        }
        return "Service " + service + " is UNKNOWN.";
    }
}
