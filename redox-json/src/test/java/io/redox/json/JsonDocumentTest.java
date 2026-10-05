package io.redox.json;

import io.redox.core.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonDocumentTest {

    @Test
    void parsesNull() {
        try (var doc = JsonDocument.parse("null")) {
            assertTrue(doc.root().isNull());
        }
    }

    @Test
    void parsesBooleans() {
        try (var doc = JsonDocument.parse("true")) {
            DElement root = doc.root();
            assertFalse(root.isNull());
            assertTrue(root.getBoolean());
        }
        try (var doc = JsonDocument.parse("false")) {
            assertFalse(doc.root().getBoolean());
        }
    }

    @Test
    void parsesInteger() {
        try (var doc = JsonDocument.parse("42")) {
            assertEquals(42, doc.root().getInt());
        }
        try (var doc = JsonDocument.parse("-7")) {
            assertEquals(-7, doc.root().getInt());
        }
    }

    @Test
    void parsesFloat() {
        try (var doc = JsonDocument.parse("3.14")) {
            assertEquals(3.14, doc.root().getDouble(), 1e-10);
        }
    }

    @Test
    void parsesString() {
        try (var doc = JsonDocument.parse("\"hello\"")) {
            assertEquals("hello", doc.root().getString());
        }
    }

    @Test
    void parsesStringWithEscapes() {
        try (var doc = JsonDocument.parse("\"line1\\nline2\"")) {
            assertEquals("line1\nline2", doc.root().getString());
        }
        try (var doc = JsonDocument.parse("\"tab\\there\"")) {
            assertEquals("tab\there", doc.root().getString());
        }
    }

    @Test
    void parsesUnicodeEscape() {
        try (var doc = JsonDocument.parse("\"\\u0041\"")) {  // 'A'
            assertEquals("A", doc.root().getString());
        }
    }

    @Test
    void parsesEmptyArray() {
        try (var doc = JsonDocument.parse("[]")) {
            DElement root = doc.root();
            assertTrue(root.isArray());
            assertEquals(0, root.asArray().size());
        }
    }

    @Test
    void parsesIntArray() {
        try (var doc = JsonDocument.parse("[1,2,3]")) {
            DArray arr = doc.root().asArray();
            assertEquals(3, arr.size());
            assertEquals(1, arr.get(0).getInt());
            assertEquals(2, arr.get(1).getInt());
            assertEquals(3, arr.get(2).getInt());
        }
    }

    @Test
    void parsesEmptyObject() {
        try (var doc = JsonDocument.parse("{}")) {
            DElement root = doc.root();
            assertTrue(root.isObject());
            assertEquals(0, root.asObject().size());
        }
    }

    @Test
    void parsesSimpleObject() {
        try (var doc = JsonDocument.parse("{\"name\":\"Alice\",\"age\":30}")) {
            DObject obj = doc.root().asObject();
            assertEquals(2, obj.size());
            assertEquals("Alice", obj.get("name").getString());
            assertEquals(30, obj.get("age").getInt());
        }
    }

    @Test
    void parsesNestedObject() {
        String json = "{\"user\":{\"id\":1,\"active\":true},\"count\":5}";
        try (var doc = JsonDocument.parse(json)) {
            DObject root = doc.root().asObject();
            DObject user = root.get("user").asObject();
            assertEquals(1, user.get("id").getInt());
            assertTrue(user.get("active").getBoolean());
            assertEquals(5, root.get("count").getInt());
        }
    }

    @Test
    void parsesArrayOfObjects() {
        String json = "[{\"x\":1},{\"x\":2},{\"x\":3}]";
        try (var doc = JsonDocument.parse(json)) {
            DArray arr = doc.root().asArray();
            assertEquals(3, arr.size());
            for (int i = 0; i < 3; i++) {
                assertEquals(i + 1, arr.get(i).asObject().get("x").getInt());
            }
        }
    }

    @Test
    void parsesNullValue() {
        try (var doc = JsonDocument.parse("{\"key\":null}")) {
            DElement val = doc.root().asObject().get("key");
            assertTrue(val.isNull());
            assertNull(val.getString());
        }
    }

    @Test
    void iteratesObject() {
        try (var doc = JsonDocument.parse("{\"a\":1,\"b\":2}")) {
            int sum = 0;
            for (DProperty p : doc.root().asObject()) {
                sum += p.value().getInt();
            }
            assertEquals(3, sum);
        }
    }

    @Test
    void iteratesArray() {
        try (var doc = JsonDocument.parse("[10,20,30]")) {
            int sum = 0;
            for (DElement e : doc.root().asArray()) {
                sum += e.getInt();
            }
            assertEquals(60, sum);
        }
    }

    @Test
    void missingKeyReturnsUndefined() {
        try (var doc = JsonDocument.parse("{\"a\":1}")) {
            DElement missing = doc.root().asObject().get("z");
            assertFalse(missing.isValid());
        }
    }

    @Test
    void throwsOnInvalidJson() {
        assertThrows(JsonParseException.class, () -> JsonDocument.parse("{bad}"));
    }

    @Test
    void parsesLargeNestedJson() {
        // Stress test: 1000-element array
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(i).append(",\"val\":\"item").append(i).append("\"}");
        }
        sb.append("]");
        try (var doc = JsonDocument.parse(sb.toString())) {
            DArray arr = doc.root().asArray();
            assertEquals(1000, arr.size());
            assertEquals(999, arr.get(999).asObject().get("id").getInt());
        }
    }
}
