package io.redox.json;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonSerializerTest {

    // ── Simple POJO ───────────────────────────────────────────────────────

    static class Point {
        public double x;
        public double y;
        Point() {}
        Point(double x, double y) { this.x = x; this.y = y; }
    }

    static class Person {
        public String      name;
        public int         age;
        public boolean     active;
        public List<String> tags;
        public Point       location;
        Person() {}
    }

    // ── Deserialize tests ─────────────────────────────────────────────────

    @Test void deserializeScalars() {
        Point p = JsonSerializer.deserialize(Point.class, "{\"x\":1.5,\"y\":-3.0}");
        assertEquals(1.5,  p.x, 1e-9);
        assertEquals(-3.0, p.y, 1e-9);
    }

    @Test void deserializeString() {
        Person p = JsonSerializer.deserialize(Person.class,
                "{\"name\":\"Alice\",\"age\":30,\"active\":true}");
        assertEquals("Alice", p.name);
        assertEquals(30, p.age);
        assertTrue(p.active);
    }

    @Test void deserializeList() {
        Person p = JsonSerializer.deserialize(Person.class,
                "{\"name\":\"Bob\",\"age\":25,\"active\":false,\"tags\":[\"java\",\"json\"]}");
        assertNotNull(p.tags);
        assertEquals(2, p.tags.size());
        assertEquals("java", p.tags.get(0));
        assertEquals("json", p.tags.get(1));
    }

    @Test void deserializeNestedPojo() {
        Person p = JsonSerializer.deserialize(Person.class,
                "{\"name\":\"Carol\",\"age\":40,\"active\":true," +
                "\"location\":{\"x\":51.5,\"y\":-0.1}}");
        assertNotNull(p.location);
        assertEquals(51.5, p.location.x, 1e-9);
        assertEquals(-0.1, p.location.y, 1e-9);
    }

    @Test void deserializeNullField() {
        Person p = JsonSerializer.deserialize(Person.class,
                "{\"name\":null,\"age\":10,\"active\":false}");
        assertNull(p.name);
    }

    @Test void deserializeUnknownFieldsIgnored() {
        // extra fields in JSON should not throw
        Point p = JsonSerializer.deserialize(Point.class,
                "{\"x\":1.0,\"y\":2.0,\"z\":3.0,\"label\":\"origin\"}");
        assertEquals(1.0, p.x, 1e-9);
        assertEquals(2.0, p.y, 1e-9);
    }

    // ── Serialize tests ───────────────────────────────────────────────────

    @Test void serializePoint() {
        Point p = new Point(1.5, -3.0);
        String json = JsonSerializer.serializeToString(p);
        // re-deserialize and verify
        Point p2 = JsonSerializer.deserialize(Point.class, json);
        assertEquals(p.x, p2.x, 1e-9);
        assertEquals(p.y, p2.y, 1e-9);
    }

    @Test void serializeNull() {
        assertEquals("null", JsonSerializer.serializeToString(null));
    }

    @Test void serializePrimitivesRoundTrip() {
        Person orig = new Person();
        orig.name   = "Dave";
        orig.age    = 28;
        orig.active = true;

        String json = JsonSerializer.serializeToString(orig);
        Person back = JsonSerializer.deserialize(Person.class, json);
        assertEquals("Dave", back.name);
        assertEquals(28, back.age);
        assertTrue(back.active);
    }

    @Test void serializeWithEscapedString() {
        Person p = new Person();
        p.name = "He said \"hello\"";
        String json = JsonSerializer.serializeToString(p);
        assertTrue(json.contains("\\\""), "Expected escaped quotes in: " + json);
        Person back = JsonSerializer.deserialize(Person.class, json);
        assertEquals("He said \"hello\"", back.name);
    }

    // ── DOM writer round-trip on datasets ─────────────────────────────────

    @Test void domRoundTripTwitter() throws Exception {
        byte[] bytes;
        try (var is = getClass().getResourceAsStream("/datasets/twitter.json")) {
            assertNotNull(is);
            bytes = is.readAllBytes();
        }
        try (var doc = JsonDocument.parse(bytes)) {
            byte[] written = new JsonWriter().toBytes(doc.root());
            assertTrue(written.length > 100_000, "Written output looks too small");
        }
    }
}
