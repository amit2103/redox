package io.redox.json;

import io.redox.core.DElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonWriterTest {

    private final JsonWriter writer = new JsonWriter();

    private String roundTrip(String json) {
        try (JsonDocument doc = JsonDocument.parse(json)) {
            return writer.toJsonString(doc.root());
        }
    }

    @Test void roundTripNull()    { assertEquals("null",  roundTrip("null")); }
    @Test void roundTripTrue()    { assertEquals("true",  roundTrip("true")); }
    @Test void roundTripFalse()   { assertEquals("false", roundTrip("false")); }
    @Test void roundTripInt()     { assertEquals("42",    roundTrip("42")); }
    @Test void roundTripNeg()     { assertEquals("-7",    roundTrip("-7")); }
    @Test void roundTripFloat()   { assertEquals("3.14",  roundTrip("3.14")); }
    @Test void roundTripString()  { assertEquals("\"hello\"", roundTrip("\"hello\"")); }

    @Test void roundTripEscaped() {
        // The source has an escape; we decode then re-encode
        String out = roundTrip("\"line1\\nline2\"");
        assertTrue(out.contains("\\n"), "Expected escaped newline in: " + out);
    }

    @Test void roundTripArray() {
        String out = roundTrip("[1,2,3]");
        assertEquals("[1,2,3]", out);
    }

    @Test void roundTripObject() {
        String out = roundTrip("{\"a\":1,\"b\":true}");
        assertEquals("{\"a\":1,\"b\":true}", out);
    }

    @Test void roundTripNested() {
        String src = "{\"user\":{\"id\":1,\"name\":\"Alice\"},\"scores\":[10,20,30]}";
        assertEquals(src, roundTrip(src));
    }

    @Test void roundTripDatasets() throws Exception {
        for (String ds : new String[]{"twitter", "citm_catalog", "canada"}) {
            byte[] bytes;
            try (var is = getClass().getResourceAsStream("/datasets/" + ds + ".json")) {
                assertNotNull(is, ds + " not found");
                bytes = is.readAllBytes();
            }
            try (JsonDocument doc = JsonDocument.parse(bytes)) {
                byte[] written = writer.toBytes(doc.root());
                // re-parse the written output and verify it produces the same root type
                try (JsonDocument doc2 = JsonDocument.parse(written)) {
                    DElement r1 = doc.root();
                    DElement r2 = doc2.root();
                    assertEquals(r1.isArray(),  r2.isArray(),  ds + " root type mismatch");
                    assertEquals(r1.isObject(), r2.isObject(), ds + " root type mismatch");
                }
            }
        }
    }
}
