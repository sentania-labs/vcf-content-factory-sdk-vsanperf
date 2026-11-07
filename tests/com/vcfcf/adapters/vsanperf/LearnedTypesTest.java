package com.vcfcf.adapters.vsanperf;

import java.util.HashSet;
import java.util.Set;

/**
 * T1: the learned-empty state machine (design v2 B7 and §Failure handling,
 * as clarified in review 2026-11-06), on an explicit clock: learned on the
 * 3rd consecutive empty answer; data or a fault resets the count; skipped
 * for 24 h plus a per-(cluster, type) offset under an hour; an empty
 * re-probe re-learns at once; a re-probe with data clears it; core types
 * never learned on an empty echo; ioinsight never learned at all; fault
 * learning spread by the same offset (review N1).
 */
public final class LearnedTypesTest {

    static final long H = 60L * 60 * 1000;

    /** Build 19, design v3 Q5: the nine strike types learn after three consecutive faults. */
    static void strikes() {
        LearnedTypes l = new LearnedTypes();
        String c = "domain-c6004";
        EntityType t = EntityType.CMMDS_WORKLOAD;
        long now = 1_000_000_000_000L;
        T.check(!l.strike(c, t, now) && !l.strike(c, t, now), "two faults: counted only");
        T.eq(2, l.strikes(c, t), "two in a row");
        l.noStrike(c, t);
        T.eq(0, l.strikes(c, t), "any other answer starts over");
        T.check(!l.strike(c, t, now) && !l.strike(c, t, now), "two again");
        T.check(!l.skip(c, t, now), "not skipped before learning");
        T.check(l.strike(c, t, now), "the third in a row learns it");
        T.check(l.skip(c, t, now + 1), "skipped");
        long until = now + LearnedTypes.SKIP_MS + LearnedTypes.offsetMs(c, t);
        T.check(l.skip(c, t, until - 1), "for 24 h plus the spread offset");
        T.check(!l.skip(c, t, until), "re-probed after");
        T.check(l.strike(c, t, until), "a re-probe that faults re-learns at once");
        T.eq(0, l.streak(c, t), "the empty count is separate");
        l.clear();
        T.eq(0, l.strikes(c, t), "clear (a new engine) forgets strikes");
    }

    public static void main(String[] args) {
        strikes();
        LearnedTypes l = new LearnedTypes();
        String c = "domain-c6004";
        EntityType t = EntityType.VSAN_DIRECT_HOST;
        long now = 1_000_000_000_000L;

        T.check(!l.empty(c, t, now), "1st empty: counted only");
        T.check(!l.empty(c, t, now + 5), "2nd empty: counted only");
        T.eq(2, l.streak(c, t), "two in a row");
        l.answered(c, t);
        T.eq(0, l.streak(c, t), "data resets the count");
        T.check(!l.empty(c, t, now) && !l.empty(c, t, now), "two again");
        T.check(!l.skip(c, t, now), "not skipped before learning");
        T.check(l.empty(c, t, now), "3rd consecutive empty learns");

        long offset = LearnedTypes.offsetMs(c, t);
        T.check(offset >= 0 && offset < H && offset % 60_000 == 0, "offset whole minutes < 1 h");
        T.check(l.skip(c, t, now + 1), "skipped");
        T.check(l.skip(c, t, now + 24 * H + offset - 1), "skipped until 24 h + offset");
        T.check(!l.skip(c, t, now + 24 * H + offset), "re-probe after 24 h + offset");
        long reprobe = now + 24 * H + offset;
        T.check(l.empty(c, t, reprobe), "an empty re-probe re-learns at once");
        T.check(l.skip(c, t, reprobe + 1), "skipped again");
        T.check(!l.skip(c, t, reprobe + 24 * H + offset), "next re-probe");
        l.answered(c, t);
        T.check(!l.empty(c, t, reprobe) && !l.empty(c, t, reprobe), "data at the re-probe: "
                + "the full count again");
        T.check(l.empty(c, t, reprobe), "third");

        // A different cluster has its own state.
        T.check(!l.skip("domain-c1", t, now + 1), "per cluster");

        // Never on an empty echo: the three core types; never at all: ioinsight.
        for (EntityType core : new EntityType[] {EntityType.HOST_DOMOWNER,
                EntityType.HOST_DOMCLIENT, EntityType.VSAN_PNIC_NET}) {
            for (int i = 0; i < 10; i++) T.check(!l.empty(c, core, now), core.id + " never");
            T.check(!l.skip(c, core, now + 1), core.id + " queried");
        }
        for (int i = 0; i < 10; i++) T.check(!l.empty(c, EntityType.IOINSIGHT, now), "ioinsight");
        T.check(!l.learn(c, EntityType.IOINSIGHT, now), "ioinsight not learned from a fault");
        T.check(!l.skip(c, EntityType.IOINSIGHT, now + 1), "ioinsight queried every cycle");

        // Fault learning, core types too: 24 h plus the offset (review N1).
        long fo = LearnedTypes.offsetMs(c, EntityType.HOST_DOMCLIENT);
        T.check(l.learn(c, EntityType.HOST_DOMCLIENT, now), "fault learns (first: log)");
        T.check(!l.learn(c, EntityType.HOST_DOMCLIENT, now), "second: no log");
        T.check(l.skip(c, EntityType.HOST_DOMCLIENT, now + 24 * H + fo - 1), "24 h + offset");
        T.check(!l.skip(c, EntityType.HOST_DOMCLIENT, now + 24 * H + fo), "then probed");

        // Re-probes are spread: 70 clusters learned in the same cycle do not
        // all come back in the same 5-minute cycle.
        Set<Long> offsets = new HashSet<>();
        for (int i = 0; i < 70; i++) offsets.add(LearnedTypes.offsetMs("domain-c" + (6000 + i), t));
        T.check(offsets.size() >= 30, "offsets spread over the hour: " + offsets.size());
        T.eq(LearnedTypes.offsetMs(c, t), LearnedTypes.offsetMs(c, t), "stable");

        l.clear();
        T.check(!l.skip(c, t, now + 1), "clear forgets");
        System.out.println("LearnedTypesTest: all assertions passed");
    }
}
