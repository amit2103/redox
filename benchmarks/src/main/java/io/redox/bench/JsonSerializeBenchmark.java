package io.redox.bench;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.redox.bench.model.TwitterResponse;
import io.redox.json.JsonSerializer;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Serialize a pre-built POJO back to JSON bytes.
 * The POJO is deserialized once in setup(), then each benchmark re-serializes it.
 *
 * Run: java -jar benchmarks/target/benchmarks.jar JsonSerializeBenchmark
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class JsonSerializeBenchmark {

    private TwitterResponse twitterPojo;
    private ObjectMapper    jackson;
    private Gson            gson;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        byte[] bytes;
        try (InputStream is = getClass().getResourceAsStream("/datasets/twitter.json")) {
            bytes = (is != null) ? is.readAllBytes() : "{}".getBytes();
        }
        jackson = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        gson = new Gson();

        // All libraries bind the same source — pick Jackson for the setup POJO
        twitterPojo = jackson.readValue(bytes, TwitterResponse.class);
    }

    @Benchmark
    public byte[] redox() {
        return JsonSerializer.serialize(twitterPojo);
    }

    @Benchmark
    public byte[] jackson() throws IOException {
        return jackson.writeValueAsBytes(twitterPojo);
    }

    @Benchmark
    public byte[] gson() {
        return gson.toJson(twitterPojo).getBytes(StandardCharsets.UTF_8);
    }

    @Benchmark
    public byte[] fastjson2() {
        return JSON.toJSONBytes(twitterPojo);
    }
}
