package pl.onyxdev.szafa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Jukebox {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Sound[] SOUNDS = {
            Sound.BLOCK_NOTE_BLOCK_HARP, Sound.BLOCK_NOTE_BLOCK_BASS, Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
            Sound.BLOCK_NOTE_BLOCK_SNARE, Sound.BLOCK_NOTE_BLOCK_HAT, Sound.BLOCK_NOTE_BLOCK_GUITAR,
            Sound.BLOCK_NOTE_BLOCK_FLUTE, Sound.BLOCK_NOTE_BLOCK_BELL, Sound.BLOCK_NOTE_BLOCK_CHIME,
            Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_COW_BELL,
            Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, Sound.BLOCK_NOTE_BLOCK_BIT, Sound.BLOCK_NOTE_BLOCK_BANJO,
            Sound.BLOCK_NOTE_BLOCK_PLING};
    public static final double RADIUS = 28;

    public record Request(Song song, UUID by, String byName) {
    }

    private final SunSzafa plugin;
    public final String world;
    public final int x, y, z;
    private TextDisplay holo;
    private Interaction hit;

    private Request current;
    private double pos;
    private int lastPlayed = -1;
    private final Deque<Request> queue = new ArrayDeque<>();
    private int age;

    public Jukebox(SunSzafa plugin, String world, int x, int y, int z) {
        this.plugin = plugin;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String serialize() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public Location center() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }

    public boolean isLoaded() {
        World w = Bukkit.getWorld(world);
        return w != null && w.isChunkLoaded(x >> 4, z >> 4);
    }

    public boolean at(org.bukkit.block.Block b) {
        return b.getWorld().getName().equals(world) && b.getX() == x && b.getY() == y && b.getZ() == z;
    }

    public UUID hitboxId() {
        return hit == null ? null : hit.getUniqueId();
    }

    void tick() {
        if (!isLoaded()) return;
        age++;
        if (age % 20 == 0) ensureEntities();
        if (current == null) {
            if (!queue.isEmpty()) start(queue.poll());
            else if (age % 20 == 0) updateHolo();
            return;
        }
        Song s = current.song();
        pos += s.tempoAt(pos) / 20.0;
        int until = Math.min((int) Math.floor(pos), s.length() - 1);
        Location c = center();
        List<Player> listeners = listeners(c);
        int particles = 0;
        for (int t = lastPlayed + 1; t <= until; t++) {
            List<Song.Note> notes = s.ticks.get(t);
            if (notes == null) continue;
            for (Song.Note n : notes) {
                float pitch = (float) Math.pow(2.0, (SongReader.fold(n.key()) - 45) / 12.0);
                Sound snd = SOUNDS[Math.max(0, Math.min(15, n.instrument()))];
                for (Player p : listeners) p.playSound(c, snd, SoundCategory.RECORDS, Math.max(0.05f, n.volume()) * 1.8f, pitch);
                if (particles < 2) {
                    particles++;
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    Location at = c.clone().add(r.nextDouble(-0.45, 0.45), 0.75 + r.nextDouble(0.3), r.nextDouble(-0.45, 0.45));

                    c.getWorld().spawnParticle(Particle.NOTE, at, 0, ((n.key() - 33) % 24) / 24.0, 0, 0, 1);
                }
            }
        }
        lastPlayed = Math.max(lastPlayed, until);
        if (age % 10 == 0) updateHolo();
        if (lastPlayed >= s.length() - 1) finish();
    }

    private List<Player> listeners(Location c) {
        List<Player> out = new ArrayList<>();
        for (Player p : c.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(c) <= RADIUS * RADIUS && !plugin.isMuted(p.getUniqueId())) out.add(p);
        }
        return out;
    }

    public Request current() {
        return current;
    }

    public List<Request> queue() {
        return List.copyOf(queue);
    }

    public int enqueue(Request r) {
        if (current == null && queue.isEmpty()) {
            start(r);
            return 0;
        }
        queue.add(r);
        return queue.size();
    }

    public boolean hasRequestFrom(UUID id) {
        return queue.stream().anyMatch(r -> r.by().equals(id));
    }

    public boolean isQueuedOrPlaying(Song s) {
        return (current != null && current.song() == s) || queue.stream().anyMatch(r -> r.song() == s);
    }

    private void start(Request r) {
        current = r;
        pos = 0;
        lastPlayed = -1;
        Location c = center();
        if (c == null) return;
        c.getWorld().playSound(c, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, 0.8f, 1.6f);
        c.getWorld().spawnParticle(Particle.NOTE, c.clone().add(0, 1, 0), 8, 0.4, 0.3, 0.4, 1);
        Component msg = MM.deserialize("<#ffc02e>♪</#ffc02e> <gray>Szafa gra: <white>" + MM.escapeTags(r.song().title)
                + "</white>" + (r.song().author.isBlank() ? "" : " <dark_gray>— " + MM.escapeTags(r.song().author))
                + " <dark_gray>(puścił(a) " + MM.escapeTags(r.byName()) + ")");
        for (Player p : listeners(c)) pl.onyxdev.suncore.SunCore.messages().actionBar(p, SunSzafa.NOW_PLAYING, msg);
        updateHolo();
    }

    private void finish() {
        current = null;
        if (queue.isEmpty()) updateHolo();
    }

    public void skip() {
        current = null;
        updateHolo();
    }

    public void stop() {
        current = null;
        queue.clear();
        updateHolo();
    }

    private void ensureEntities() {
        Location c = center();
        if (c == null) return;
        if (holo == null || !holo.isValid()) {
            holo = c.getWorld().spawn(c.clone().add(0, 1.15, 0), TextDisplay.class, d -> {
                plugin.tag(d);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(100, 12, 8, 20));
                d.setShadowed(true);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(0.5f);
            });
            updateHolo();
        }
        if (hit == null || !hit.isValid()) {
            hit = c.getWorld().spawn(c.clone().add(0, -0.52, 0), Interaction.class, e -> {
                plugin.tag(e);
                e.setInteractionWidth(1.06f);
                e.setInteractionHeight(1.06f);
                e.setResponsive(true);
            });
        }
    }

    private void updateHolo() {
        if (holo == null || !holo.isValid()) return;
        if (current == null) {
            holo.text(MM.deserialize("<gradient:#ffe066:#ff9a1f><b>♪ Szafa grająca ♪</b></gradient>\n"
                    + "<gray>PPM: <white>wybierz utwór</white>"));
            return;
        }
        Song s = current.song();
        double f = Math.min(1, pos / Math.max(1, s.length()));
        int bars = 14, full = (int) Math.round(f * bars);
        String bar = "<#ffc02e>" + "▮".repeat(full) + "</#ffc02e><dark_gray>" + "▮".repeat(bars - full) + "</dark_gray>";
        int now = (int) Math.round(f * s.seconds());
        String time = (now / 60) + ":" + String.format("%02d", now % 60) + " / " + s.duration();
        String next = queue.isEmpty() ? "" : "\n<dark_gray>Następnie: <gray>" + MM.escapeTags(queue.peek().song().title);
        String note = age / 10 % 2 == 0 ? "♪" : "♫";
        holo.text(MM.deserialize("<#ffc02e>" + note + "</#ffc02e> <white>" + MM.escapeTags(s.title) + "</white> <#ffc02e>" + note + "</#ffc02e>\n"
                + (s.author.isBlank() ? "" : "<gray>" + MM.escapeTags(s.author) + "\n")
                + bar + " <gray>" + time + next));
    }

    public void remove() {
        if (holo != null) holo.remove();
        if (hit != null) hit.remove();
        holo = null;
        hit = null;
    }
}
