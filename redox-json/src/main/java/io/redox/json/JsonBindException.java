package io.redox.json;

public class JsonBindException extends RuntimeException {
    public JsonBindException(String message) { super(message); }
    public JsonBindException(String message, Throwable cause) { super(message, cause); }
}
