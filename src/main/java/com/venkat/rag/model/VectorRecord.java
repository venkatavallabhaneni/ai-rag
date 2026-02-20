package com.venkat.rag.model;

import java.util.List;

public record VectorRecord(String id, List<Double> vector, Chunk payload) {}
