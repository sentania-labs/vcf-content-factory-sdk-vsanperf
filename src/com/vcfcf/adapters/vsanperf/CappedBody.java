package com.vcfcf.adapters.vsanperf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.zip.GZIPInputStream;

/**
 * Response body handler with a size cap, after the template's
 * {@code BoundedBody} (issue #12), with the two deviations the vSphere
 * JSON API needs (design "Response handling"):
 * <ol>
 *   <li>an error status (400 and up) body is read too, up to a small cap
 *       ({@link #FAULT_CAP}), because vSphere faults arrive as HTTP 500
 *       with a JSON fault body the client must dispatch on;</li>
 *   <li>the body is kept as raw bytes and gzip-decoded afterwards
 *       ({@link #decode}) through a second cap on the decoded size, since
 *       every request sends {@code Accept-Encoding: gzip} and the JDK
 *       client does not decode it.</li>
 * </ol>
 * Any other status (204, 3xx) is cancelled unread. Over a cap the body is
 * marked, never failed, so the send completes once (no retry fetches an
 * oversized body again); the caller throws. Plain JDK, no SDK.
 */
final class CappedBody implements HttpResponse.BodyHandler<CappedBody.Body> {

    /** 200 bodies on the wire. The lab's largest Build 1 call was 4.5 KB. */
    static final long WIRE_CAP = 4L * 1024 * 1024;
    /** 200 bodies after gzip decoding. The lab's largest was 64 KB. */
    static final long DECODED_CAP = 8L * 1024 * 1024;
    /** Error bodies (faults), wire and decoded. */
    static final long FAULT_CAP = 64L * 1024;

    private final long okCap;
    private final long faultCap;

    CappedBody() {
        this(WIRE_CAP, FAULT_CAP);
    }

    CappedBody(long okCap, long faultCap) {
        if (okCap <= 0 || faultCap <= 0) throw new IllegalArgumentException("caps must be > 0");
        this.okCap = okCap;
        this.faultCap = faultCap;
    }

    /** A read body: its raw bytes, or the mark that it was over the cap. */
    static final class Body {
        /** Raw (possibly gzip) bytes; {@code null} when unread or over the cap. */
        final byte[] bytes;
        final boolean overCap;

        Body(byte[] bytes, boolean overCap) {
            this.bytes = bytes;
            this.overCap = overCap;
        }

        static final Body UNREAD = new Body(null, false);
    }

    @Override
    public HttpResponse.BodySubscriber<Body> apply(HttpResponse.ResponseInfo info) {
        int status = info.statusCode();
        long cap;
        if (status == 200) {
            cap = okCap;
        } else if (status >= 400) {
            cap = faultCap;
        } else {
            return new Unread();
        }
        long declared = info.headers().firstValueAsLong("Content-Length").orElse(-1L);
        return new Capped(cap, declared > cap);
    }

    /** Whether a {@code Content-Encoding} header value says gzip. */
    static boolean isGzip(String contentEncoding) {
        return contentEncoding != null
                && contentEncoding.trim().toLowerCase(Locale.ROOT).equals("gzip");
    }

    /**
     * The body as UTF-8 text, gunzipped when {@code gzip}; {@code null}
     * when the decoded size passes {@code decodedCap} (reading stops there).
     *
     * @throws IOException when a gzip body is corrupt
     */
    static String decode(byte[] raw, boolean gzip, long decodedCap) throws IOException {
        if (raw == null) return null;
        if (!gzip) {
            return raw.length > decodedCap ? null : new String(raw, StandardCharsets.UTF_8);
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(raw.length * 8, 1 << 20));
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > decodedCap) return null;
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Cancels on subscribe: no byte is requested. */
    static final class Unread implements HttpResponse.BodySubscriber<Body> {
        private final CompletableFuture<Body> result = new CompletableFuture<>();

        @Override public void onSubscribe(Flow.Subscription s) {
            s.cancel();
            result.complete(Body.UNREAD);
        }

        @Override public void onNext(List<ByteBuffer> items) {}

        @Override public void onError(Throwable t) {
            result.complete(Body.UNREAD);
        }

        @Override public void onComplete() {
            result.complete(Body.UNREAD);
        }

        @Override public CompletionStage<Body> getBody() {
            return result;
        }
    }

    /** Collects up to the cap, then cancels and marks. */
    static final class Capped implements HttpResponse.BodySubscriber<Body> {
        private final long cap;
        private final boolean declaredOverCap;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private final CompletableFuture<Body> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        Capped(long cap, boolean declaredOverCap) {
            this.cap = cap;
            this.declaredOverCap = declaredOverCap;
        }

        @Override public void onSubscribe(Flow.Subscription s) {
            this.subscription = s;
            if (declaredOverCap) {
                s.cancel();
                result.complete(new Body(null, true));
                return;
            }
            s.request(Long.MAX_VALUE);
        }

        @Override public void onNext(List<ByteBuffer> items) {
            if (result.isDone()) return;
            for (ByteBuffer b : items) {
                if (buf.size() + (long) b.remaining() > cap) {
                    subscription.cancel();
                    result.complete(new Body(null, true));
                    return;
                }
                byte[] chunk = new byte[b.remaining()];
                b.get(chunk);
                buf.write(chunk, 0, chunk.length);
            }
        }

        @Override public void onError(Throwable t) {
            result.completeExceptionally(t);
        }

        @Override public void onComplete() {
            result.complete(new Body(buf.toByteArray(), false));
        }

        @Override public CompletionStage<Body> getBody() {
            return result;
        }
    }
}
