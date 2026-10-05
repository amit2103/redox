package io.redox.json;

import io.redox.core.DElement;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the parser handles all three benchmark datasets without error. */
class DatasetSmokeTest {

    @Test
    void parsesCanada() throws IOException {
        byte[] bytes = load("canada.json");
        try (JsonDocument doc = JsonDocument.parse(bytes)) {
            DElement root = doc.root();
            assertTrue(root.isObject(), "canada root should be object");
        }
    }

    @Test
    void parsesTwitter() throws IOException {
        byte[] bytes = load("twitter.json");
        try (JsonDocument doc = JsonDocument.parse(bytes)) {
            DElement root = doc.root();
            assertTrue(root.isObject(), "twitter root should be object");
        }
    }

    @Test
    void parsesCitmCatalog() throws IOException {
        byte[] bytes = load("citm_catalog.json");
        try (JsonDocument doc = JsonDocument.parse(bytes)) {
            DElement root = doc.root();
            assertTrue(root.isObject(), "citm_catalog root should be object");
        }
    }

    private byte[] load(String name) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/datasets/" + name)) {
            assertNotNull(is, "Dataset not found: " + name);
            return is.readAllBytes();
        }
    }
}
