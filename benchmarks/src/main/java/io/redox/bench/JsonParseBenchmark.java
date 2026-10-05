package io.redox.bench;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonParser;
import io.redox.json.JsonDocument;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks raw JSON parse throughput (no POJO binding) against:
 *   - Gson JsonParser
 *   - Jackson ObjectMapper.readTree()
 *   - FastJSON2 JSON.parse()
 *   - REDox JsonDocument.parse()
 *
 * Run with: java -jar benchmarks/target/benchmarks.jar JsonParseBenchmark
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
public class JsonParseBenchmark {

    // ── Datasets ─────────────────────────────────────────────────────────

    @Param({"twitter", "citm_catalog", "canada"})
    public String dataset;

    private byte[] jsonBytes;

    // ── Competitors ──────────────────────────────────────────────────────

    private ObjectMapper jackson;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        // Load dataset from classpath resources (add to src/main/resources)
        try (var stream = getClass().getResourceAsStream("/datasets/" + dataset + ".json")) {
            if (stream == null) {
                // fallback: synthetic payload for CI/quick runs
                jsonBytes = buildSyntheticJson(10_000).getBytes(StandardCharsets.UTF_8);
            } else {
                jsonBytes = stream.readAllBytes();
            }
        }
        jackson = new ObjectMapper();
    }

    // ── Benchmarks ────────────────────────────────────────────────────────

    @Benchmark
    public Object redox() {
        try (JsonDocument doc = JsonDocument.parse(jsonBytes)) {
            return doc.root();
        }
    }

    @Benchmark
    public JsonNode jackson() throws IOException {
        return jackson.readTree(jsonBytes);
    }

    @Benchmark
    public com.google.gson.JsonElement gson() {
        return JsonParser.parseString(new String(jsonBytes, StandardCharsets.UTF_8));
    }

    @Benchmark
    public Object fastjson2() {
        return JSON.parse(jsonBytes);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String buildSyntheticJson(int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(i)
              .append(",\"name\":\"item").append(i).append('"')
              .append(",\"value\":").append(i * 1.5)
              .append(",\"active\":").append(i % 2 == 0)
              .append('}');
        }
        return sb.append(']').toString();
    }
}
