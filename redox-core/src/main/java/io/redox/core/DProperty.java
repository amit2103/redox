package io.redox.core;

/** A key-value pair yielded when iterating a DObject. */
public final class DProperty {
    private final DElement key;
    private final DElement value;

    public DProperty(DElement key, DElement value) {
        this.key   = key;
        this.value = value;
    }

    public DElement key()       { return key; }
    public DElement value()     { return value; }
    public String   keyString() { return key.getString(); }
}
