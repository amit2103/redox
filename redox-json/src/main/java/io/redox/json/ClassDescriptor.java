package io.redox.json;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached reflection metadata for a single class used during POJO binding.
 *
 * Built once per class, stored in a ConcurrentHashMap.  All Field objects
 * are made accessible up-front so per-value reflection overhead is minimal.
 */
final class ClassDescriptor {

    // ── Global cache ──────────────────────────────────────────────────────

    private static final ConcurrentHashMap<Class<?>, ClassDescriptor> CACHE =
            new ConcurrentHashMap<>();

    static ClassDescriptor of(Class<?> cls) {
        return CACHE.computeIfAbsent(cls, ClassDescriptor::new);
    }

    // ── FieldInfo ─────────────────────────────────────────────────────────

    static final class FieldInfo {
        private final Field    field;
        private final String   jsonName;
        private final byte[]   jsonNameBytes;   // raw UTF-8 name bytes for zero-alloc key matching
        private final byte[]   encodedKeyBytes; // pre-encoded `"name":` for the writer
        private final boolean  skipNull;
        private final Class<?> rawType;
        private final Type     genericType;

        FieldInfo(Field field) {
            this.field       = field;
            this.jsonName    = field.getName(); // default: use field name (no annotation yet)
            this.skipNull    = false;
            this.rawType     = field.getType();
            this.genericType = field.getGenericType();

            this.jsonNameBytes  = jsonName.getBytes(StandardCharsets.UTF_8);
            // pre-build `"name":` bytes
            String key = '"' + jsonName + '"' + ':';
            this.encodedKeyBytes = key.getBytes(StandardCharsets.UTF_8);

            field.setAccessible(true);
        }

        Field    field()           { return field; }
        String   jsonName()        { return jsonName; }
        byte[]   encodedKeyBytes() { return encodedKeyBytes; }
        boolean  skipNull()        { return skipNull; }
        Class<?> rawType()         { return rawType; }
        Type     genericType()     { return genericType; }
    }

    // ── Instance ──────────────────────────────────────────────────────────

    private final Class<?>              cls;
    private final List<FieldInfo>       fieldList;
    private final Map<String, FieldInfo> byName;

    private ClassDescriptor(Class<?> cls) {
        this.cls      = cls;
        this.fieldList = new ArrayList<>();
        this.byName   = new HashMap<>();

        // Walk class hierarchy (not interfaces) collecting all declared fields
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            for (Field f : cur.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) continue;
                FieldInfo fi = new FieldInfo(f);
                fieldList.add(fi);
                byName.putIfAbsent(fi.jsonName(), fi);
            }
            cur = cur.getSuperclass();
        }
    }

    List<FieldInfo>      fields()       { return fieldList; }
    FieldInfo            get(String key) { return byName.get(key); }
    Class<?>             cls()          { return cls; }

    /**
     * Match a key DElement against all known fields using raw byte comparison.
     * Avoids any String allocation; returns null if no field matches.
     */
    FieldInfo matchRawKey(io.redox.core.DElement keyElement) {
        for (FieldInfo fi : fieldList) {
            if (keyElement.rawMatchesBytes(fi.jsonNameBytes)) return fi;
        }
        return null;
    }

    /** Create a new instance using the no-arg constructor. */
    Object newInstance() {
        try {
            Constructor<?> ctor = cls.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new JsonBindException("Cannot instantiate " + cls.getName()
                + ": needs a no-arg constructor", e);
        }
    }
}
