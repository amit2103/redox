package io.redox.json;

public class JsonParseException extends RuntimeException {
    private final int position;

    public JsonParseException(String message, int position) {
        super(message + " (at byte offset " + position + ")");
        this.position = position;
    }

    public int position() { return position; }
}
