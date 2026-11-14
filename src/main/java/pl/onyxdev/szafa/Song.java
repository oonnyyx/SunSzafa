package pl.onyxdev.szafa;

import java.util.ArrayList;
import java.util.List;

public final class Song {
    public record Note(int instrument, double key, float volume) {
    }

    public final String id;
    public final String title;
    public final String author;

    public final double tempoStart;
    public final double tempoEnd;
    public final List<List<Note>> ticks;
    public final int noteCount;

    public Song(String id, String title, String author, double tempoStart, double tempoEnd, List<List<Note>> ticks) {
        this.id = id;
        this.title = title == null || title.isBlank() ? id : title.strip();
        this.author = author == null ? "" : author.strip();
        this.tempoStart = clampTempo(tempoStart);
        this.tempoEnd = clampTempo(tempoEnd);
        this.ticks = ticks;
        int n = 0;
        for (List<Note> l : ticks) if (l != null) n += l.size();
        this.noteCount = n;
    }

    private static double clampTempo(double t) {
        return Math.max(0.5, Math.min(60, t <= 0 ? 10 : t));
    }

    public int length() {
        return ticks.size();
    }

    public double tempoAt(double tick) {
        if (tempoStart == tempoEnd || ticks.isEmpty()) return tempoStart;
        double f = Math.max(0, Math.min(1, tick / ticks.size()));
        return tempoStart + (tempoEnd - tempoStart) * f;
    }

    public double seconds() {
        return ticks.size() / ((tempoStart + tempoEnd) / 2.0);
    }

    public String duration() {
        int s = (int) Math.round(seconds());
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    static List<List<Note>> emptyTicks(int length) {
        List<List<Note>> l = new ArrayList<>(length);
        for (int i = 0; i < length; i++) l.add(null);
        return l;
    }

    static void add(List<List<Note>> ticks, int tick, Note n) {
        while (ticks.size() <= tick) ticks.add(null);
        List<Note> l = ticks.get(tick);
        if (l == null) ticks.set(tick, l = new ArrayList<>(2));
        l.add(n);
    }
}
