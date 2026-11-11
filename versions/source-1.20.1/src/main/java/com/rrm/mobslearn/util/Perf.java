package com.rrm.mobslearn.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny always-on profiler: rolling per-section timings, shown by /mobslearn perf. */
public final class Perf {
    private static final class Section {
        double avgNs, peakNs;
        long callsInTick, lastTick = -1;
        double avgCalls;
    }

    private static final Map<String, Section> sections = new LinkedHashMap<>();
    private static final Map<String, Double> counters = new LinkedHashMap<>();
    private static final Map<String, Long> pending = new LinkedHashMap<>();
    private static long tick;
    private static final double ALPHA = 0.02;

    private Perf() {
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void stop(String name, long started) {
        long ns = System.nanoTime() - started;
        Section s = sections.computeIfAbsent(name, k -> new Section());
        Long acc = pending.get(name);
        pending.put(name, (acc == null ? 0 : acc) + ns);
        if (s.lastTick != tick) {
            s.lastTick = tick;
            s.callsInTick = 0;
        }
        s.callsInTick++;
    }

    /** Adds to a per-tick counter (e.g. mobs processed). */
    public static void count(String name, double amount) {
        Long acc = pending.get("#" + name);
        pending.put("#" + name, (acc == null ? 0 : acc) + Math.round(amount * 1000));
    }

    /** Called once at the end of every server tick: folds this tick into the rolling averages. */
    public static void endTick() {
        tick++;
        for (Map.Entry<String, Section> e : sections.entrySet()) {
            Long ns = pending.get(e.getKey());
            double v = ns == null ? 0 : ns;
            Section s = e.getValue();
            s.avgNs += (v - s.avgNs) * ALPHA;
            s.peakNs = Math.max(v, s.peakNs * 0.995);
        }
        for (Map.Entry<String, Long> e : pending.entrySet()) {
            if (!e.getKey().startsWith("#")) continue;
            String k = e.getKey().substring(1);
            double v = e.getValue() / 1000.0;
            counters.merge(k, v, (old, now) -> old + (now - old) * ALPHA);
        }
        for (Map.Entry<String, Double> e : counters.entrySet()) {
            if (!pending.containsKey("#" + e.getKey())) e.setValue(e.getValue() * (1 - ALPHA));
        }
        pending.clear();
    }

    public static void reset() {
        sections.clear();
        counters.clear();
        pending.clear();
    }

    public static double totalMs() {
        double sum = 0;
        for (Map.Entry<String, Section> e : sections.entrySet()) if (!e.getKey().contains(".")) sum += e.getValue().avgNs;
        return sum / 1.0e6;
    }

    public static java.util.List<String> report() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(String.format(java.util.Locale.ROOT, "World Learns: %.3f ms/tick on average (of 50 ms budget)", totalMs()));
        for (Map.Entry<String, Section> e : sections.entrySet()) {
            Section s = e.getValue();
            out.add(String.format(java.util.Locale.ROOT, "  %-18s avg %7.3f ms   peak %7.3f ms", e.getKey(), s.avgNs / 1.0e6, s.peakNs / 1.0e6));
        }
        for (Map.Entry<String, Double> e : counters.entrySet()) {
            out.add(String.format(java.util.Locale.ROOT, "  %-18s %8.1f per tick", e.getKey(), e.getValue()));
        }
        return out;
    }
}
