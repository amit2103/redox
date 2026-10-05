package io.redox.json;

import io.redox.core.DElement;

/**
 * Implemented by every APT-generated converter class.
 * Generated converters are named {@code {ClassName}DoxConverter} and registered
 * with {@link DoxConverterRegistry} via their static initializer.
 */
public interface DoxConverter<T> {

    T deserialize(DElement element);

    void serializeTo(T obj, JsonWriter writer);

    default byte[] serialize(T obj) {
        JsonWriter w = new JsonWriter();
        serializeTo(obj, w);
        return w.finishBytes();
    }
}
