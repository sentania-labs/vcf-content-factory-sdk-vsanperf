package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.json.SimpleJson;

import java.io.IOException;
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
 * <p><b>Value selection.</b> Per series, the newest non-{@code None}
 * token with its own timestamp; an all-{@code None} series yields no
 * sample (never a zero). Per-mille labels are converted here. A label not
 * in the catalog is dropped (the labels filter means none arrive).
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
        /** Entities whose id was unusable (another type, malformed pNIC id). */
        final int malformed;
        /** Series seen / series that produced a sample. */
        final int seriesSeen;
        final int seriesKept;

        Result(List<Entity> entities, int echoes, int malformed, int seriesSeen,
                int seriesKept) {
            this.entities = Collections.unmodifiableList(entities);
            this.echoes = echoes;
            this.malformed = malformed;
            this.seriesSeen = seriesSeen;
            this.seriesKept = seriesKept;
        }
    }

    /**
     * Parse a 200 body.
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
        List<Entity> out = new ArrayList<>();
        int echoes = 0;
        int malformed = 0;
        int seen = 0;
        int kept = 0;
        for (SimpleJson e : root.asList()) {
            String refId = e.get("entityRefId").asString();
            SimpleJson series = e.get("value");
            if (refId != null && refId.endsWith(":*")) {
                echoes++;
                continue;
            }
            EntityRef ref = EntityRef.parse(refId, type);
            if (ref == null) {
                malformed++;
                continue;
            }
            long[] times = sampleTimes(e.get("sampleInfo").asString());
            List<Sample> samples = new ArrayList<>();
            for (SimpleJson s : series.asList()) {
                seen++;
                String label = s.get("metricId").get("label").asString();
                EntityType.Metric metric = label == null ? null : type.metric(label);
                if (metric == null) continue;
                Sample sample = newest(metric, times, s.get("values").asString());
                if (sample != null) {
                    samples.add(sample);
                    kept++;
                }
            }
            out.add(new Entity(ref, samples));
        }
        return new Result(out, echoes, malformed, seen, kept);
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
     * The value with the newest usable timestamp, chosen by time, not by CSV
     * position (review N3: every capture is ascending, but nothing promises
     * it). {@code None}, blanks, non-numbers and non-finite numbers are
     * skipped; on equal times the later position wins.
     */
    static Sample newest(EntityType.Metric metric, long[] times, String values) {
        if (values == null) return null;
        String[] tokens = values.split(",", -1);
        int n = Math.min(tokens.length, times.length);
        long bestTime = Long.MIN_VALUE;
        Double best = null;
        for (int i = 0; i < n; i++) {
            if (times[i] == Long.MIN_VALUE || times[i] < bestTime) continue;
            Double v = number(tokens[i]);
            if (v != null) {
                bestTime = times[i];
                best = v;
            }
        }
        return best == null ? null : new Sample(metric, bestTime, metric.convert(best));
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
