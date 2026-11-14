package pl.onyxdev.szafa;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SongReader {
    private SongReader() {
    }

    public static Song read(Path file) throws IOException {
        String name = file.getFileName().toString();
        String id = name.replaceFirst("\\.[^.]+$", "");
        if (name.toLowerCase(Locale.ROOT).endsWith(".nbs")) return nbs(id, Files.readAllBytes(file));
        return txt(id, Files.readString(file, StandardCharsets.UTF_8));
    }

    public static Song nbs(String id, byte[] bytes) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        try {
            int first = b.getShort() & 0xFFFF;
            int version = 0;
            int vanilla = 10;
            int length;
            if (first == 0) {
                version = b.get() & 0xFF;
                vanilla = b.get() & 0xFF;
                length = version >= 3 ? b.getShort() & 0xFFFF : 0;
            } else {
                length = first;
            }
            int layerCount = b.getShort() & 0xFFFF;
            String title = str(b);
            String author = str(b);
            String original = str(b);
            str(b);
            double tempo = (b.getShort() & 0xFFFF) / 100.0;
            b.get();
            b.get();
            b.get();
            b.getInt();
            b.getInt();
            b.getInt();
            b.getInt();
            b.getInt();
            str(b);
            if (version >= 4) {
                b.get();
                b.get();
                b.getShort();
            }

            record Raw(int tick, int layer, int instrument, int key, int velocity, int pitch) {
            }
            List<Raw> raw = new ArrayList<>();
            int tick = -1;
            int maxLayer = 0;
            while (true) {
                int jump = b.getShort() & 0xFFFF;
                if (jump == 0) break;
                tick += jump;
                int layer = -1;
                while (true) {
                    int jl = b.getShort() & 0xFFFF;
                    if (jl == 0) break;
                    layer += jl;
                    int instrument = b.get() & 0xFF;
                    int key = b.get() & 0xFF;
                    int velocity = 100, pitch = 0;
                    if (version >= 4) {
                        velocity = b.get() & 0xFF;
                        b.get();
                        pitch = b.getShort();
                    }
                    raw.add(new Raw(tick, layer, instrument, key, velocity, pitch));
                    maxLayer = Math.max(maxLayer, layer);
                }
            }
            int[] layerVolume = new int[Math.max(layerCount, maxLayer + 1)];
            java.util.Arrays.fill(layerVolume, 100);
            try {
                for (int i = 0; i < layerCount; i++) {
                    str(b);
                    if (version >= 4) b.get();
                    layerVolume[i] = b.get() & 0xFF;
                    if (version >= 2) b.get();
                }
            } catch (RuntimeException ignored) {
            }

            List<List<Song.Note>> ticks = Song.emptyTicks(Math.max(length, tick) + 1);
            for (Raw r : raw) {
                int instrument = r.instrument() < vanilla && r.instrument() < 16 ? r.instrument() : 0;
                float vol = (layerVolume[r.layer()] / 100f) * (r.velocity() / 100f);
                if (vol <= 0.01f) continue;
                Song.add(ticks, r.tick(), new Song.Note(instrument, r.key() + r.pitch() / 100.0, vol));
            }
            String shownAuthor = !original.isBlank() ? original : author;
            return new Song(id, title.isBlank() ? id : title, shownAuthor, tempo, tempo, ticks);
        } catch (java.nio.BufferUnderflowException e) {
            throw new IOException("Uszkodzony albo nieobsługiwany plik .nbs");
        }
    }

    private static String str(ByteBuffer b) {
        int len = b.getInt();
        if (len < 0 || len > b.remaining()) throw new java.nio.BufferUnderflowException();
        byte[] s = new byte[len];
        b.get(s);
        return new String(s, StandardCharsets.UTF_8);
    }

    static final Map<String, Integer> INSTRUMENTS = new HashMap<>();

    static {
        String[] names = {"harp", "bass", "basedrum", "snare", "hat", "guitar", "flute", "bell", "chime",
                "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling"};
        for (int i = 0; i < names.length; i++) INSTRUMENTS.put(names[i], i);
    }

    public static Song txt(String id, String text) throws IOException {
        String title = id, author = "";
        double tempo = 4, tempoEnd = -1;
        Map<String, List<String>> tracks = new java.util.LinkedHashMap<>();
        Map<String, Float> volumes = new HashMap<>();
        for (String line : text.split("\\R")) {
            String l = line.strip();
            if (l.isEmpty()) continue;
            if (l.startsWith("#")) {
                String[] kv = l.substring(1).split(":", 2);
                if (kv.length < 2) continue;
                String k = kv[0].strip().toLowerCase(Locale.ROOT), v = kv[1].strip();
                switch (k) {
                    case "tytul", "tytuł", "title" -> title = v;
                    case "autor", "author" -> author = v;
                    case "tempo" -> tempo = Double.parseDouble(v);
                    case "tempo-koniec" -> tempoEnd = Double.parseDouble(v);
                    default -> {
                    }
                }
                continue;
            }
            int colon = l.indexOf(':');
            if (colon < 0) throw new IOException("Linia bez instrumentu: " + l);
            String head = l.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            float vol = 1f;
            if (head.contains("@")) {
                vol = Float.parseFloat(head.substring(head.indexOf('@') + 1));
                head = head.substring(0, head.indexOf('@'));
            }
            if (!INSTRUMENTS.containsKey(head)) throw new IOException("Nieznany instrument: " + head);
            volumes.put(head, vol);
            List<String> tokens = tracks.computeIfAbsent(head, k -> new ArrayList<>());
            for (String t : l.substring(colon + 1).trim().split("\\s+")) {
                if (!t.isEmpty() && !t.equals("|")) tokens.add(t);
            }
        }
        List<List<Song.Note>> ticks = new ArrayList<>();
        for (var e : tracks.entrySet()) {
            int instrument = INSTRUMENTS.get(e.getKey());
            List<String> tokens = e.getValue();

            List<int[]> keys = new ArrayList<>();
            for (int step = 0; step < tokens.size(); step++) {
                String t = tokens.get(step);
                if (t.equals("-") || t.equals(".")) continue;
                for (String n : t.split("\\+")) keys.add(new int[]{step, key(n)});
            }
            int shift = bestShift(keys);
            for (int[] k : keys) {
                Song.add(ticks, k[0], new Song.Note(instrument, fold(k[1] + shift), volumes.getOrDefault(e.getKey(), 1f)));
            }
            while (ticks.size() < tokens.size()) ticks.add(null);
        }
        return new Song(id, title, author, tempo, tempoEnd > 0 ? tempoEnd : tempo, ticks);
    }

    static int key(String note) throws IOException {
        try {
            String n = note.strip();
            int semi = switch (Character.toUpperCase(n.charAt(0))) {
                case 'C' -> 0;
                case 'D' -> 2;
                case 'E' -> 4;
                case 'F' -> 5;
                case 'G' -> 7;
                case 'A' -> 9;
                case 'B', 'H' -> 11;
                default -> throw new IOException("Zła nuta: " + note);
            };
            int i = 1;
            while (i < n.length() && (n.charAt(i) == '#' || n.charAt(i) == 'b')) {
                semi += n.charAt(i) == '#' ? 1 : -1;
                i++;
            }
            int octave = Integer.parseInt(n.substring(i));
            return 12 * (octave + 1) + semi - 21;
        } catch (RuntimeException e) {
            throw new IOException("Zła nuta: " + note);
        }
    }

    private static int bestShift(List<int[]> keys) {
        int best = 0, bestIn = -1;
        for (int s = -48; s <= 48; s += 12) {
            int in = 0;
            for (int[] k : keys) if (k[1] + s >= 33 && k[1] + s <= 57) in++;
            if (in > bestIn || (in == bestIn && Math.abs(s) < Math.abs(best))) {
                best = s;
                bestIn = in;
            }
        }
        return best;
    }

    static double fold(double key) {
        while (key < 33) key += 12;
        while (key > 57) key -= 12;
        return key;
    }
}
