package com.vcfcf.adapters.vsanperf;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The adapter instance configuration (design "Settings"), validated on
 * construction. Immutable; plain JDK.
 *
 * <p><b>Messages never echo a rejected value</b>: an operator may paste a
 * URL with a password into the vCenter field, and these messages reach the
 * log and Test Connection. Each names the rule broken. The password is
 * private, read only through {@link #password()} by the client builder,
 * and {@link #toString()} masks it. // REDACT-SECRET
 */
final class VsanPerfConfig {

    static final int DEFAULT_WORKERS = 4;
    static final int MAX_WORKERS = 16;
    static final int DEFAULT_CALL_TIMEOUT_S = 30;
    static final int MAX_CALL_TIMEOUT_S = 600;
    /** Connect timeout (SKILL "HTTP client timeouts": a few seconds). */
    static final int CONNECT_TIMEOUT_S = 5;
    /** Below the interval by this much when the deadline setting is 0. */
    static final int DEADLINE_MARGIN_S = 60;
    /** Deadline floor, so a 1-minute interval still gets a usable cycle. */
    static final int MIN_DEADLINE_S = 30;
    static final int DEFAULT_VM_EVERY_N = 3;
    static final int MAX_VM_EVERY_N = 12;

    /** As entered ({@code host} or {@code host:port}): also the TLS name-check host. */
    final String vcenterHost;
    final String username;
    private final String password; // REDACT-SECRET
    final String clusterInclude;
    final String clusterExclude;
    final int workers;
    final int callTimeoutSeconds;
    /** 0 = the collection interval minus 60 s. */
    final int cycleDeadlineSeconds;
    final boolean perfTimingLogging;
    /** Build 10 (v2 "Settings"): the host tier, default on. */
    final boolean tierHost;
    /** Build 10: the cluster tier, default on. */
    final boolean tierCluster;
    /** Build 11: the disk tier, default off. */
    final boolean tierDisk;
    /** Build 11: the VM tier, default off. */
    final boolean tierVm;
    /** Build 11: the VM tier runs on every Nth cycle per cluster, staggered (1 to 12, default 3). */
    final int vmEveryN;
    /**
     * Build 19 (design v3 "Setting: metric_scope"): {@code dashboard} (the
     * default, and the value of an account saved before 1.2.0) or {@code all}.
     * Re-read on reconfigure like the tiers: a saved change builds a new
     * engine, so learned types and dedup marks restart.
     */
    final EntityType.Scope scope;
    /** The entity types this instance queries, in collection order (never empty). */
    final List<EntityType> types;
    /** Entity type ids in Entity types to exclude that this build does not know (configure WARN). */
    final List<String> unknownExcludes;
    private final String baseUrl;

    private static final Pattern HOSTNAME = Pattern.compile(
            "(?=.{1,253}$)[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
            + "(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("\\[[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*\\]");

    VsanPerfConfig(String vcenterHost, String username, String password, String include,
            String exclude, String workers, String callTimeout, String cycleDeadline,
            String perfTiming) {
        this(vcenterHost, username, password, include, exclude, workers, callTimeout,
                cycleDeadline, perfTiming, null, null, null);
    }

    VsanPerfConfig(String vcenterHost, String username, String password, String include,
            String exclude, String workers, String callTimeout, String cycleDeadline,
            String perfTiming, String tierHost, String tierCluster, String excludeTypes) {
        this(vcenterHost, username, password, include, exclude, workers, callTimeout,
                cycleDeadline, perfTiming, tierHost, tierCluster, null, null, null, excludeTypes);
    }

    VsanPerfConfig(String vcenterHost, String username, String password, String include,
            String exclude, String workers, String callTimeout, String cycleDeadline,
            String perfTiming, String tierHost, String tierCluster, String tierDisk,
            String tierVm, String vmEveryN, String excludeTypes) {
        this(vcenterHost, username, password, include, exclude, workers, callTimeout,
                cycleDeadline, perfTiming, tierHost, tierCluster, tierDisk, tierVm, vmEveryN,
                excludeTypes, null);
    }

    VsanPerfConfig(String vcenterHost, String username, String password, String include,
            String exclude, String workers, String callTimeout, String cycleDeadline,
            String perfTiming, String tierHost, String tierCluster, String tierDisk,
            String tierVm, String vmEveryN, String excludeTypes, String metricScope) {
        this.vcenterHost = validHost(vcenterHost);
        this.baseUrl = buildBaseUrl(this.vcenterHost);
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("the vCenter user name is empty");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("the vCenter password is empty");
        }
        this.username = username.trim();
        this.password = password; // REDACT-SECRET
        this.clusterInclude = include == null ? "" : include.trim();
        this.clusterExclude = exclude == null ? "" : exclude.trim();
        this.workers = intSetting(workers, DEFAULT_WORKERS, 1, MAX_WORKERS, "Parallel clusters");
        this.callTimeoutSeconds = intSetting(callTimeout, DEFAULT_CALL_TIMEOUT_S, 1,
                MAX_CALL_TIMEOUT_S, "Call timeout (s)");
        this.cycleDeadlineSeconds = intSetting(cycleDeadline, 0, 0, 86_400, "Cycle deadline (s)");
        this.perfTimingLogging = parseFlag(perfTiming);
        this.tierHost = tierFlag(tierHost, "Host tier", true);
        this.tierCluster = tierFlag(tierCluster, "Cluster tier", true);
        this.tierDisk = tierFlag(tierDisk, "Disk tier", false);
        this.tierVm = tierFlag(tierVm, "VM tier", false);
        this.vmEveryN = intSetting(vmEveryN, DEFAULT_VM_EVERY_N, 1, MAX_VM_EVERY_N,
                "VM tier every N cycles");
        this.scope = scopeSetting(metricScope);
        Set<EntityType.Tier> tiers = EnumSet.of(EntityType.Tier.CORE);
        if (this.tierHost) tiers.add(EntityType.Tier.HOST);
        if (this.tierCluster) tiers.add(EntityType.Tier.CLUSTER);
        if (this.tierDisk) tiers.add(EntityType.Tier.DISK);
        if (this.tierVm) tiers.add(EntityType.Tier.VM);
        // v3 composition: tiers, then the scope's types, then exclude_types,
        // which may name any of the 69 ids under either scope (a new-type id
        // under dashboard is known and silently not in scope).
        List<EntityType> scope = EntityType.ofTiers(tiers, this.scope);
        List<String> unknown = new ArrayList<>();
        for (String id : ClusterFilter.items(excludeTypes)) {
            EntityType t = EntityType.byId(id.toLowerCase(Locale.ROOT));
            if (t == null) unknown.add(id);
            else scope.remove(t);
        }
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("Entity types to exclude leaves no entity type "
                    + "in scope with the tiers switched on; nothing would be collected");
        }
        this.types = Collections.unmodifiableList(scope);
        this.unknownExcludes = Collections.unmodifiableList(unknown);
    }

    /**
     * The configure WARN for exclude_types entries this build does not know
     * (v2 "Settings": a WARN, not an error). Names are echoed only when they
     * look like entity type ids, so pasted text is never logged.
     */
    String unknownExcludesWarning() {
        StringBuilder names = new StringBuilder();
        int other = 0;
        for (String u : unknownExcludes) {
            if (u.matches("[A-Za-z0-9-]{1,64}")) {
                if (names.length() > 0) names.append(", ");
                names.append(u);
            } else {
                other++;
            }
        }
        return "Entity types to exclude: " + unknownExcludes.size() + " entr"
                + (unknownExcludes.size() == 1 ? "y is" : "ies are")
                + " not an entity type this build collects, ignored"
                + (names.length() > 0 ? " (" + names + ")" : "")
                + (other > 0 ? "; " + other + " not shown (not an entity type id)" : "");
    }

    /**
     * Metric scope: empty (an account saved before 1.2.0) is {@code dashboard};
     * otherwise {@code dashboard} or {@code all}, any case.
     */
    static EntityType.Scope scopeSetting(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return EntityType.Scope.DASHBOARD;
        EntityType.Scope sc = EntityType.Scope.parse(s);
        if (sc == null) throw new IllegalArgumentException("Metric scope must be dashboard or all");
        return sc;
    }

    /**
     * A tier switch: empty (an account saved before the setting existed) is
     * the default; otherwise {@code true} or {@code false}, any case.
     */
    static boolean tierFlag(String raw, String label, boolean dflt) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return dflt;
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        throw new IllegalArgumentException(label + " must be true or false");
    }

    /** The strict on/off parse of perf_timing_logging: only {@code true}, any case. */
    static boolean parseFlag(String raw) {
        return "true".equalsIgnoreCase(raw);
    }

    String password() {
        return password; // REDACT-SECRET
    }

    String baseUrl() {
        return baseUrl;
    }

    /**
     * The collector object's {@code cluster_scope} identifier: Clusters to
     * include exactly as the account stores it, trimmed only, or {@code *}
     * when empty (never empty, so the identifier always has a value). Not
     * normalised (build 8): the account's identity is the raw string, so
     * {@code "cl01,cl02"} and {@code "cl01, cl02"} are two accounts and must
     * be two objects. Normalisation is for the filter only ({@link #filter}).
     */
    String clusterScope() {
        return clusterInclude.isEmpty() ? "*" : clusterInclude;
    }

    ClusterFilter filter() {
        return new ClusterFilter(clusterInclude, clusterExclude);
    }

    /**
     * The cycle budget in seconds: the setting, or the interval minus 60 s
     * (design "Timeouts and deadline"), never under 30 s.
     */
    long deadlineSeconds(Integer intervalSeconds) {
        if (cycleDeadlineSeconds > 0) return cycleDeadlineSeconds;
        int interval = intervalSeconds == null || intervalSeconds <= 0 ? 300 : intervalSeconds;
        return Math.max(MIN_DEADLINE_S, interval - DEADLINE_MARGIN_S);
    }

    @Override
    public String toString() {
        return "vcenter=" + vcenterHost + " " + filter() + " workers=" + workers
                + " callTimeout=" + callTimeoutSeconds + "s cycleDeadline="
                + (cycleDeadlineSeconds == 0 ? "interval-60s" : cycleDeadlineSeconds + "s")
                + " perfTimingLogging=" + perfTimingLogging + " tierHost=" + tierHost
                + " tierCluster=" + tierCluster + " tierDisk=" + tierDisk + " tierVm=" + tierVm
                + " vmEveryN=" + vmEveryN + " scope=" + scope.label + " types=" + types.size();
    }

    static int intSetting(String raw, int dflt, int min, int max, String label) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return dflt;
        int n;
        try {
            n = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a whole number from " + min
                    + " to " + max);
        }
        if (n < min || n > max) {
            throw new IllegalArgumentException(label + " must be from " + min + " to " + max);
        }
        return n;
    }

    /** {@code host} or {@code host:port}; a bracketed IPv6 address may carry a port. */
    static String validHost(String raw) {
        String h = raw == null ? "" : raw.trim();
        if (h.isEmpty()) throw new IllegalArgumentException("vCenter Server is required");
        if (h.indexOf('@') >= 0) {
            throw new IllegalArgumentException("vCenter Server must not contain a user name "
                    + "or password ('@')");
        }
        if (h.contains("://") || h.indexOf('/') >= 0 || h.indexOf('?') >= 0
                || h.indexOf('#') >= 0) {
            throw new IllegalArgumentException("vCenter Server must be a host name or "
                    + "address, optionally with :port, not a URL");
        }
        for (int i = 0; i < h.length(); i++) {
            if (Character.isWhitespace(h.charAt(i))) {
                throw new IllegalArgumentException("vCenter Server must not contain spaces");
            }
        }
        String host = h;
        String port = null;
        if (h.startsWith("[")) {
            int close = h.indexOf(']');
            if (close < 0) throw new IllegalArgumentException("vCenter Server has an "
                    + "unclosed IPv6 bracket");
            host = h.substring(0, close + 1);
            String rest = h.substring(close + 1);
            if (!rest.isEmpty()) {
                if (!rest.startsWith(":")) throw new IllegalArgumentException("vCenter "
                        + "Server: only :port may follow a bracketed IPv6 address");
                port = rest.substring(1);
            }
            if (!IPV6.matcher(host).matches()) {
                throw new IllegalArgumentException("vCenter Server is not a valid bracketed "
                        + "IPv6 address");
            }
        } else {
            int colon = h.indexOf(':');
            if (colon >= 0) {
                if (h.indexOf(':', colon + 1) >= 0) {
                    throw new IllegalArgumentException("vCenter Server: write an IPv6 "
                            + "address in brackets, for example [2001:db8::10]");
                }
                host = h.substring(0, colon);
                port = h.substring(colon + 1);
            }
            if (IPV4.matcher(host).matches()) {
                for (String part : host.split("\\.")) {
                    if (Integer.parseInt(part) > 255) {
                        throw new IllegalArgumentException("vCenter Server is not a valid "
                                + "IPv4 address");
                    }
                }
            } else if (!HOSTNAME.matcher(host).matches()
                    || host.substring(host.lastIndexOf('.') + 1).chars()
                            .allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("vCenter Server is not a valid host name "
                        + "or IPv4 address");
            }
        }
        if (port != null) {
            int n;
            try {
                n = Integer.parseInt(port);
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (n < 1 || n > 65535) {
                throw new IllegalArgumentException("vCenter Server port must be from 1 to "
                        + "65535");
            }
        }
        return h;
    }

    /**
     * {@code https://host:port/} for the entered host (port 443 when none),
     * the URL VCF Operations reviews on Validate Connection so an untrusted
     * certificate brings up the accept dialog; {@code null} when invalid.
     */
    static String rootUrl(String raw) {
        String h;
        try {
            h = validHost(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
        boolean hasPort = h.startsWith("[") ? h.indexOf("]:") > 0 : h.indexOf(':') > 0;
        return "https://" + h + (hasPort ? "" : ":443") + "/";
    }

    private static String buildBaseUrl(String hostPort) {
        try {
            URI u = new URI("https://" + hostPort);
            if (u.getHost() == null) throw new URISyntaxException(hostPort, "no host");
            return "https://" + hostPort;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("vCenter Server does not form a valid URL");
        }
    }
}
