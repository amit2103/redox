package io.redox.bench;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.redox.bench.model.CanadaGeo;
import io.redox.bench.model.TwitterResponse;
import io.redox.json.JsonSerializer;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Compares APT-generated REDox converter against Jackson, Gson, and FastJSON2.
 * The generated converter eliminates all reflection from the binding path.
 *
 * Run: java -jar benchmarks/target/benchmarks.jar JsonDeserializeGeneratedBenchmark
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class JsonDeserializeGeneratedBenchmark {

    private byte[]       twitterBytes;
    private byte[]       canadaBytes;
    private String       twitterString;
    private String       canadaString;
    private ObjectMapper jackson;
    private Gson         gson;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        twitterBytes  = load("twitter.json");
        canadaBytes   = load("canada.json");
        twitterString = new String(twitterBytes, StandardCharsets.UTF_8);
        canadaString  = new String(canadaBytes,  StandardCharsets.UTF_8);
        jackson = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        gson = new Gson();
    }

    // ── twitter.json → TwitterResponse ───────────────────────────────────

    @Benchmark public TwitterResponse redox_gen_twitter()  { return JsonSerializer.deserialize(TwitterResponse.class, twitterBytes); }
    @Benchmark public TwitterResponse jackson_twitter()    throws IOException { return jackson.readValue(twitterBytes, TwitterResponse.class); }
    @Benchmark public TwitterResponse gson_twitter()       { return gson.fromJson(twitterString, TwitterResponse.class); }
    @Benchmark public TwitterResponse fastjson2_twitter()  { return JSON.parseObject(twitterBytes, TwitterResponse.class); }

    // ── canada.json → CanadaGeo ───────────────────────────────────────────

    @Benchmark public CanadaGeo redox_gen_canada()   { return JsonSerializer.deserialize(CanadaGeo.class, canadaBytes); }
    @Benchmark public CanadaGeo jackson_canada()     throws IOException { return jackson.readValue(canadaBytes, CanadaGeo.class); }
    @Benchmark public CanadaGeo gson_canada()        { return gson.fromJson(canadaString, CanadaGeo.class); }
    @Benchmark public CanadaGeo fastjson2_canada()   { return JSON.parseObject(canadaBytes, CanadaGeo.class); }

    private byte[] load(String name) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/datasets/" + name)) {
            if (is == null) throw new IOException("Dataset not found: " + name);
            return is.readAllBytes();
        }
    }
}
