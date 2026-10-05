package io.redox.json;

import io.redox.core.*;

import java.lang.reflect.*;
import java.util.*;

/**
 * Converts between a REDox DOM (DElement) and Java POJOs using reflection.
 *
 * Supports:
 *   - Primitives and their boxed forms: int, long, double, float, boolean, String
 *   - java.util.List (element type inferred from generic type parameter)
 *   - Any POJO with a no-arg constructor (recursive, handles nesting)
 *   - null propagation
 */
final class ReflectiveConverter {

    private ReflectiveConverter() {}

    // ── Deserialize (DElement → T) ────────────────────────────────────────

    @SuppressWarnings("unchecked")
    static <T> T fromElement(DElement element, Class<T> targetType) {
        if (!element.isValid() || element.isNull()) return null;
        return (T) coerce(element, targetType, null);
    }

    /** Core recursive coercion: maps a DElement to a Java value of the given type. */
    private static Object coerce(DElement el, Class<?> rawType, Type genericType) {
        if (!el.isValid() || el.isNull()) return null;

        // ── Scalars ───────────────────────────────────────────────────────
        if (rawType == String.class) {
            if (el.isString() || el.isNull()) return el.getString();
            // JSON has a number/boolean where POJO expects String → coerce
            if (el.isNumber())  return Double.toString(el.getDouble());
            if (el.isBoolean()) return Boolean.toString(el.getBoolean());
            return el.toString();
        }
        if (rawType == int.class    || rawType == Integer.class)   return el.getInt();
        if (rawType == long.class   || rawType == Long.class)      return el.getLong();
        if (rawType == double.class || rawType == Double.class)    return el.getDouble();
        if (rawType == float.class  || rawType == Float.class)     return (float) el.getDouble();
        if (rawType == boolean.class|| rawType == Boolean.class)   return el.getBoolean();
        if (rawType == byte.class   || rawType == Byte.class)      return (byte)  el.getInt();
        if (rawType == short.class  || rawType == Short.class)     return (short) el.getInt();
        if (rawType == char.class   || rawType == Character.class) {
            String s = el.getString();
            return (s != null && !s.isEmpty()) ? s.charAt(0) : '\0';
        }
        if (rawType == Number.class) return el.getDouble();
        if (rawType == Object.class) return toGenericObject(el);

        // ── List ──────────────────────────────────────────────────────────
        if (List.class.isAssignableFrom(rawType)) {
            return toList(el, genericType);
        }

        // ── Array ─────────────────────────────────────────────────────────
        if (rawType.isArray()) {
            return toArray(el, rawType.getComponentType());
        }

        // ── POJO ──────────────────────────────────────────────────────────
        if (!rawType.isPrimitive() && !rawType.isEnum()) {
            return toPojo(el, rawType);
        }

        return null;
    }

    private static List<Object> toList(DElement el, Type genericType) {
        if (!el.isArray()) return Collections.emptyList();
        // Infer element type from the generic parameter if available
        Class<?> elementType = Object.class;
        if (genericType instanceof ParameterizedType) {
            Type arg = ((ParameterizedType) genericType).getActualTypeArguments()[0];
            if (arg instanceof Class) elementType = (Class<?>) arg;
        }
        DArray arr = el.asArray();
        List<Object> list = new ArrayList<>(arr.size());
        Class<?> elemFinal = elementType;
        for (DElement item : arr) {
            list.add(coerce(item, elemFinal, null));
        }
        return list;
    }

    private static Object toArray(DElement el, Class<?> componentType) {
        if (!el.isArray()) return Array.newInstance(componentType, 0);
        DArray arr = el.asArray();
        Object result = Array.newInstance(componentType, arr.size());
        int i = 0;
        for (DElement item : arr) {
            Array.set(result, i++, coerce(item, componentType, null));
        }
        return result;
    }

    private static Object toPojo(DElement el, Class<?> targetType) {
        if (!el.isObject()) return null;
        ClassDescriptor desc = ClassDescriptor.of(targetType);
        Object obj = desc.newInstance();
        DObject dobj = el.asObject();
        for (DProperty prop : dobj) {
            // use raw-byte key matching to avoid String allocation per property
            ClassDescriptor.FieldInfo fi = desc.matchRawKey(prop.key());
            if (fi == null) continue; // unknown field — skip
            DElement valEl = prop.value();
            Object val = coerce(valEl, fi.rawType(), fi.genericType());
            try {
                fi.field().set(obj, val);
            } catch (IllegalAccessException e) {
                // field was made accessible in ClassDescriptor; shouldn't happen
                throw new JsonBindException("Cannot set field " + fi.field().getName(), e);
            }
        }
        return obj;
    }

    /** When target type is Object, produce the most natural Java representation. */
    private static Object toGenericObject(DElement el) {
        if (el.isNull())    return null;
        if (el.isBoolean()) return el.getBoolean();
        if (el.isString())  return el.getString();
        if (el.isNumber())  return el.getDouble();
        if (el.isArray()) {
            DArray arr = el.asArray();
            List<Object> list = new ArrayList<>(arr.size());
            for (DElement item : arr) list.add(toGenericObject(item));
            return list;
        }
        if (el.isObject()) {
            DObject obj = el.asObject();
            Map<String, Object> map = new LinkedHashMap<>();
            for (DProperty p : obj) map.put(p.keyString(), toGenericObject(p.value()));
            return map;
        }
        return null;
    }
}
