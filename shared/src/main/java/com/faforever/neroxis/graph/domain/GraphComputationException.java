package com.faforever.neroxis.graph.domain;

public class GraphComputationException extends RuntimeException {
    public GraphComputationException(String message) {
        this(message, null);
    }

    public GraphComputationException(String message, Throwable cause) {
        super(message, cause);
    }
}
