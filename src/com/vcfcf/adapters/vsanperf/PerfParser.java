package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.json.SimpleJson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses one {@code VsanPerfQueryPerf} response body (a JSON array of
 * {@code VsanPerfEntityMetricCSV}) for one entity type into compact
 * samples, so the body can be dropped at once (process and discard).
 *
 * <p>Response shape (api map "Response shape for a Java client",
 * observed): entity {@code entityRefId}, {@code sampleInfo} (CSV of UTC
 * {@code yyyy-MM-dd HH:mm:ss}, no zone), {@code value[]} of series with
 * {@code metricId.label} and {@code values} (CSV aligned to
 * {@code sampleInfo}; the literal {@code None} means no sample). An empty
 * type echoes the wildcard ({@code <type>:*}) with no {@code value}.
 *
 * <p><b>Value selection (v2 "one bucket rule", replaces Build 1's newest
 * token).</b> Per series, every non-{@code None} bucket in the window, each
 * with its own timestamp; the engine pushes those newer than the
 * per-(entity, label) mark. An all-{@code None} series yields no sample
 * (never a zero). Per-mille labels are converted here. A label not in the
 * catalog is dropped (the labels filter means none arrive).
 */
final class PerfParser {

    private static final DateTimeFormatter SAMPLE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PerfParser() {}

    /** One kept value: the catalog row, its sample time (epoch ms), the pushed value. */
    static final class Sample {
        final EntityType.Metric metric;
        final long timestamp;
        final double value;

        Sample(EntityType.Metric metric, long timestamp, double value) {
            this.metric = metric;
            this.timestamp = timestamp;
            this.value = value;
        }
    }

    /** The kept samples of one entity. */
    static final class Entity {
        final EntityRef ref;
        final List<Sample> samples;

        Entity(EntityRef ref, List<Sample> samples) {
            this.ref = ref;
            this.samples = Collections.unmodifiableList(samples);
        }
    }

    /** What one response held. */
    static final class Result {
        final List<Entity> entities;
        /** Wildcard echoes ({@code <type>:*}, no objects of the type). */
        final int echoes;
        /** Entities whose id was unusable (another type, a wrong part count). */
        final int malformed;
        /** The {@code |} part count of the first unusable id (for a WARN; never the id). */
        final int malformedParts;
        /** Series seen / series that produced at least one sample. */
        final int seriesSeen;
        final int seriesKept;
        /** Samples (non-None buckets) kept over all series. */
        final int samples;

        Result(List<Entity> entities, int echoes, int malformed, int malformedParts,
                int seriesSeen, int seriesKept, int samples) {
            this.samples = samples;
            this.entities = Collections.unmodifiableList(entities);
            this.echoes = echoes;
            this.malformed = malformed;
            this.malformedParts = malformedParts;
            this.seriesSeen = seriesSeen;
            this.seriesKept = seriesKept;
        }

        /**
         * The type's empty answer (v2 B7): the wildcard echo and nothing
         * else. A response with any entity, usable or not, is not empty.
         */
        boolean isEmptyEcho() {
            return echoes > 0 && entities.isEmpty() && malformed == 0;
        }
    }

    /**
     * Parse a 200 body held as one string (whole-document parse). Kept for
     * the tests, which check that it and the streaming parse give the same
     * rows (v2 T1); collection uses {@link #parse(InputStream, EntityType)}.
     *
     * @throws IOException when the body is not JSON or not an array: an
     *         unreadable response, never an empty success
     */
    static Result parse(String body, EntityType type) throws IOException {
        SimpleJson root;
        try {
            root = SimpleJson.parse(body);
        } catch (RuntimeException e) {
            throw new Unreadable(type.id + ": the response is not valid JSON");
        }
        if (root.isNull() || !root.isList()) {
            throw new Unreadable(type.id + ": the response is not a JSON array "
                    + "of entities; treated as unreadable");
        }
        Acc acc = new Acc(type);
        for (SimpleJson e : root.asList()) {
            List<String[]> series = new ArrayList<>();
            for (SimpleJson x : e.get("value").asList()) {
                series.add(new String[] {x.get("metricId").get("label").asString(),
                        x.get("values").asString()});
            }
            acc.entity(e.get("entityRefId").asString(), e.get("sampleInfo").asString(), series);
        }
        return acc.result();
    }

    /**
     * Parse a 200 body from its decoded stream, one
     * {@code VsanPerfEntityMetricCSV} at a time (v2 Sizing requirement 3):
     * only the current entity is held as a tree, and what is kept of it is
     * the compact samples. The result is returned only when the whole
     * document parsed, so a stream cut mid-entity fails the call and nothing
     * of the response is pushed.
     *
     * @throws IOException ({@link Unreadable}) when the body is not a JSON
     *         array or is cut; an {@link IOException} from the stream itself
     *         is passed on wrapped as {@link Unreadable}
     */
    static Result parse(InputStream decoded, EntityType type) throws IOException {
        Acc acc = new Acc(type);
        Reader r = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8),
                16 * 1024);
        try {
            new JsonStream(r).forEachInArray(e -> {
                List<String[]> series = new ArrayList<>();
                for (Object x : JsonStream.list(JsonStream.get(e, "value"))) {
                    series.add(new String[] {
                            JsonStream.str(JsonStream.get(JsonStream.get(x, "metricId"), "label")),
                            JsonStream.str(JsonStream.get(x, "values"))});
                }
                acc.entity(JsonStream.str(JsonStream.get(e, "entityRefId")),
                        JsonStream.str(JsonStream.get(e, "sampleInfo")), series);
            });
        } catch (Unreadable u) {
            throw new Unreadable(type.id + ": " + u.getMessage());
        } catch (IOException io) {
            throw new Unreadable(type.id + ": the response stream could not be read ("
                    + io.getClass().getSimpleName() + ")");
        }
        return acc.result();
    }

    /** Accumulates entities of one response into compact samples (both parses). */
    private static final class Acc {
        private final EntityType type;
        private final List<Entity> out = new ArrayList<>();
        private int echoes;
        private int malformed;
        private int malformedParts;
        private int seen;
        private int kept;
        private int samplesKept;

        Acc(EntityType type) {
            this.type = type;
        }

        /** One entity: its id, {@code sampleInfo}, and (label, values CSV) per series. */
        void entity(String refId, String sampleInfo, List<String[]> series) {
            if (refId != null && refId.endsWith(":*")) {
                echoes++;
                return;
            }
            EntityRef ref = EntityRef.parse(refId, type);
            if (ref == null) {
                if (malformed++ == 0) malformedParts = EntityRef.parts(refId);
                return;
            }
            long[] times = sampleTimes(sampleInfo);
            List<Sample> samples = new ArrayList<>();
            for (String[] s : series) {
                seen++;
                EntityType.Metric metric = s[0] == null ? null : type.metric(s[0]);
                if (metric == null) continue;
                List<Sample> b = buckets(metric, times, s[1]);
                if (!b.isEmpty()) {
                    samples.addAll(b);
                    samplesKept += b.size();
                    kept++;
                }
            }
            out.add(new Entity(ref, samples));
        }

        Result result() {
            return new Result(out, echoes, malformed, malformedParts, seen, kept, samplesKept);
        }
    }

    /**
     * The {@code sampleInfo} timestamps as epoch ms; an unparseable token
     * is {@code Long.MIN_VALUE}, which no value is ever paired with.
     */
    static long[] sampleTimes(String sampleInfo) {
        if (sampleInfo == null || sampleInfo.trim().isEmpty()) return new long[0];
        String[] tokens = sampleInfo.split(",", -1);
        long[] out = new long[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            out[i] = parseSampleTime(tokens[i]);
        }
        return out;
    }

    static long parseSampleTime(String token) {
        try {
            return LocalDateTime.parse(token.trim(), SAMPLE_TIME)
                    .toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException e) {
            return Long.MIN_VALUE;
        }
    }

    /**
     * Every usable bucket of one series, oldest first, one per timestamp
     * (v2 one bucket rule). {@code None}, blanks, non-numbers and non-finite
     * numbers are skipped, as is a token whose timestamp did not parse; on a
     * repeated timestamp the later position wins. Order by time, not by CSV
     * position (review N3: every capture is ascending, nothing promises it).
     */
    static List<Sample> buckets(EntityType.Metric metric, long[] times, String values) {
        if (values == null) return Collections.emptyList();
        String[] tokens = values.split(",", -1);
        int n = Math.min(tokens.length, times.length);
        java.util.TreeMap<Long, Double> byTime = new java.util.TreeMap<>();
        for (int i = 0; i < n; i++) {
            if (times[i] == Long.MIN_VALUE) continue;
            Double v = number(tokens[i]);
            if (v != null) byTime.put(times[i], v);
        }
        if (byTime.isEmpty()) return Collections.emptyList();
        List<Sample> out = new ArrayList<>(byTime.size());
        for (java.util.Map.Entry<Long, Double> e : byTime.entrySet()) {
            out.add(new Sample(metric, e.getKey(), metric.convert(e.getValue())));
        }
        return out;
    }

    /** A finite number, or {@code null} for {@code None}, blank or junk. */
    static Double number(String token) {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty() || "None".equals(t)) return null;
        try {
            double d = Double.parseDouble(t);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
