package com.venkat.tools;

import java.util.Map;

public interface Tool {
    String name();

    String description();

    Map<String, Object> schema(); // JSON schema for arguments

    String execute(Map<String, Object> args);
}