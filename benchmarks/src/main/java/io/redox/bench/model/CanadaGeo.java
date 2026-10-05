package io.redox.bench.model;

import io.redox.annotation.DoxSerializable;
import java.util.List;

@DoxSerializable
public class CanadaGeo {
    public String          type;
    public List<Feature>   features;

    @DoxSerializable
    public static class Feature {
        public String     type;
        public Properties properties;
        public Geometry   geometry;
    }

    @DoxSerializable
    public static class Properties {
        public String name;
    }

    @DoxSerializable
    public static class Geometry {
        public String                       type;
        public List<List<List<Double>>>     coordinates;
    }
}
