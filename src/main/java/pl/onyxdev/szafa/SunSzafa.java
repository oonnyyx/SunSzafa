package pl.onyxdev.szafa;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import pl.onyxdev.suncore.Messages;
import pl.onyxdev.suncore.SunCore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class SunSzafa extends JavaPlugin implements Listener, TabExecutor {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    static final String PREFIX = "<gradient:#ffe066:#ff9a1f><b>Szafa</b></gradient> <dark_gray>»</dark_gray> ";

    static final String NOW_PLAYING = "szafa.teraz.gra";
    static final String INFO = "szafa.info";

    private static final String[] BUILTIN = {"oda_do_radosci", "korobeiniki_tetris", "dla_elizy", "w_grocie_krola_gor",
            "happy_birthday", "wlazl_kotek", "mala_nocna_muzyka",
            "menuet_g", "jingle_bells", "panie_janie_kanon", "toccata_d_moll"};
    private static final Material[] DISCS = {Material.MUSIC_DISC_CAT, Material.MUSIC_DISC_BLOCKS, Material.MUSIC_DISC_CHIRP,
            Material.MUSIC_DISC_FAR, Material.MUSIC_DISC_MALL, Material.MUSIC_DISC_MELLOHI, Material.MUSIC_DISC_STAL,
            Material.MUSIC_DISC_STRAD, Material.MUSIC_DISC_WARD, Material.MUSIC_DISC_WAIT, Material.MUSIC_DISC_PIGSTEP,
            Material.MUSIC_DISC_OTHERSIDE, Material.MUSIC_DISC_RELIC, Material.MUSIC_DISC_CREATOR};

    private NamespacedKey fxKey;
    private final List<Song> songs = new ArrayList<>();
    private final List<Jukebox> boxes = new ArrayList<>();
    private final Set<UUID> muted = new HashSet<>();
    private File songsDir;

    @Override
    public void onEnable() {
        fxKey = new NamespacedKey(this, "efekt");
        Messages m = SunCore.messages();
        m.register(this, NOW_PLAYING, "Napis „Szafa gra: …” nad hotbarem, gdy zaczyna się nowy utwór", true, true);
        m.register(this, INFO, "Odpowiedzi szafy (dodano do kolejki, utwór już gra…)", true, false);
        songsDir = new File(getDataFolder(), "utwory");
        installBuiltins();
        loadSongs();
        loadBoxes();
        Bukkit.getPluginManager().registerEvents(this, this);
        var cmd = getCommand("szafa");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, () -> boxes.forEach(Jukebox::tick), 1, 1);
    }

    @Override
    public void onDisable() {
        boxes.forEach(Jukebox::remove);
    }

    void tag(Entity e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(fxKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void installBuiltins() {
        songsDir.mkdirs();
        Path marker = new File(getDataFolder(), ".wbudowane").toPath();
        Set<String> done = new HashSet<>();
        try {
            if (Files.exists(marker)) {
                List<String> lines = Files.readAllLines(marker);

                if (lines.isEmpty()) done.addAll(List.of(BUILTIN).subList(0, 7));
                else done.addAll(lines);
            }
        } catch (IOException ignored) {
        }
        for (String name : BUILTIN) {
            if (done.contains(name)) continue;
            try (InputStream in = getResource("wbudowane/" + name + ".txt")) {
                if (in != null) Files.copy(in, new File(songsDir, name + ".txt").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                done.add(name);
            } catch (IOException e) {
                getLogger().warning("Nie skopiowano " + name + ": " + e.getMessage());
            }
        }
        try {
            Files.write(marker, done);
        } catch (IOException ignored) {
        }
    }

    List<String> loadSongs() {
        List<String> errors = new ArrayList<>();
        List<Song> loaded = new ArrayList<>();
        File[] files = songsDir.listFiles((d, n) -> n.toLowerCase().endsWith(".nbs") || n.toLowerCase().endsWith(".txt"));
        if (files != null) {
            for (File f : files) {
                try {
                    Song s = SongReader.read(f.toPath());
                    if (s.noteCount == 0) throw new IOException("brak nut");
                    loaded.add(s);
                } catch (Exception e) {
                    errors.add(f.getName() + ": " + e.getMessage());
                }
            }
        }
        loaded.sort(Comparator.comparing(s -> s.title.toLowerCase()));
        songs.clear();
        songs.addAll(loaded);
        getLogger().info("Utwory: " + songs.size() + (errors.isEmpty() ? "" : ", błędy: " + errors));
        return errors;
    }

    private void loadBoxes() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "szafy.yml"));
        for (String s : y.getStringList("szafy")) {
            String[] p = s.split(";");
            try {
                boxes.add(new Jukebox(this, p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            } catch (Exception e) {
                getLogger().warning("Zła szafa: " + s);
            }
        }
        for (String s : y.getStringList("wyciszeni")) {
            try {
                muted.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveBoxes() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("szafy", boxes.stream().map(Jukebox::serialize).toList());
        y.set("wyciszeni", muted.stream().map(UUID::toString).toList());
        try {
            y.save(new File(getDataFolder(), "szafy.yml"));
        } catch (IOException e) {
            getLogger().warning("Nie zapisano szafy.yml: " + e.getMessage());
        }
    }

    boolean isMuted(UUID id) {
        return muted.contains(id);
    }

    private void toggleMute(Player p) {
        if (!muted.remove(p.getUniqueId())) muted.add(p.getUniqueId());
        saveBoxes();
        info(p, muted.contains(p.getUniqueId())
                ? "<gray>Muzyka z szaf <white>wyciszona</white> dla ciebie. <dark_gray>(/szafa wycisz, żeby włączyć)"
                : "<gray>Muzyka z szaf znowu <white>włączona</white>.");
    }

    private Jukebox boxAt(Block b) {
        for (Jukebox j : boxes) if (j.at(b)) return j;
        return null;
    }

    private Jukebox boxByHitbox(Entity e) {
        for (Jukebox j : boxes) if (e.getUniqueId().equals(j.hitboxId())) return j;
        return null;
    }

    private void info(Player p, String mini) {
        SunCore.messages().send(p, INFO, MM.deserialize(PREFIX + mini));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHitboxUse(PlayerInteractEntityEvent e) {
        Jukebox j = boxByHitbox(e.getRightClicked());
        if (j == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) new SongMenu(e.getPlayer(), j, 0).open();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHitboxHit(PrePlayerAttackEntityEvent e) {
        Jukebox j = boxByHitbox(e.getAttacked());
        if (j == null) return;
        e.setCancelled(true);
        new SongMenu(e.getPlayer(), j, 0).open();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlock(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || e.getHand() != EquipmentSlot.HAND) return;
        Jukebox j = boxAt(e.getClickedBlock());
        if (j == null) return;
        e.setCancelled(true);
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) new SongMenu(e.getPlayer(), j, 0).open();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Jukebox j = boxAt(e.getBlock());
        if (j == null) return;
        e.setCancelled(true);
        if (e.getPlayer().hasPermission("szafa.admin")) {
            e.getPlayer().sendMessage(MM.deserialize(PREFIX + "<gray>To szafa. Usuń ją: <white>/szafa usun</white> (patrząc na nią)."));
        }
    }

    private abstract static class Menu implements InventoryHolder {
        final Inventory inv;
        final Map<Integer, Consumer<InventoryClickEvent>> buttons = new HashMap<>();

        Menu(int rows, Component title) {
            inv = Bukkit.createInventory(this, rows * 9, title);
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final class SongMenu extends Menu {
        private final Player viewer;
        private final Jukebox box;
        private final int page;

        SongMenu(Player viewer, Jukebox box, int page) {
            super(6, MM.deserialize("<dark_gray>♪ Szafa grająca"));
            this.viewer = viewer;
            this.box = box;
            this.page = page;
        }

        void open() {
            render();
            viewer.openInventory(inv);
            viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.8f);
        }

        void set(int slot, ItemStack it, Consumer<InventoryClickEvent> click) {
            inv.setItem(slot, it);
            if (click != null) buttons.put(slot, click);
        }

        void render() {
            inv.clear();
            buttons.clear();
            int per = 45, from = page * per;
            for (int i = 0; i < per && from + i < songs.size(); i++) {
                Song s = songs.get(from + i);
                boolean playing = box.current() != null && box.current().song() == s;
                boolean queued = !playing && box.isQueuedOrPlaying(s);
                List<String> lore = new ArrayList<>();
                if (!s.author.isBlank()) lore.add("<gray>" + MM.escapeTags(s.author));
                lore.add("<dark_gray>Długość: " + s.duration());
                lore.add("");
                lore.add(playing ? "<#ffc02e>♪ Teraz gra" : queued ? "<gray>W kolejce" : "<yellow>Kliknij</yellow> <gray>— puść / dodaj do kolejki");
                set(i, item(DISCS[(from + i) % DISCS.length], "<white>" + MM.escapeTags(s.title), lore, playing),
                        e -> request(s));
            }
            ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
            for (int i = 45; i < 54; i++) inv.setItem(i, pane);
            if (page > 0) set(45, item(Material.ARROW, "<gray>← Poprzednia strona", List.of(), false),
                    e -> new SongMenu(viewer, box, page - 1).open());
            if (from + per < songs.size()) set(53, item(Material.ARROW, "<gray>Następna strona →", List.of(), false),
                    e -> new SongMenu(viewer, box, page + 1).open());

            List<String> now = new ArrayList<>();
            if (box.current() == null) now.add("<gray>Cisza. Wybierz coś!");
            else {
                now.add("<white>" + MM.escapeTags(box.current().song().title));
                now.add("<dark_gray>puścił(a) " + MM.escapeTags(box.current().byName()));
            }
            if (viewer.hasPermission("szafa.admin") && box.current() != null) now.add("<yellow>Kliknij</yellow> <gray>— pomiń (admin)");
            set(48, item(Material.JUKEBOX, "<#ffc02e><b>Teraz gra</b>", now, box.current() != null), e -> {
                if (viewer.hasPermission("szafa.admin") && box.current() != null) {
                    box.skip();
                    new SongMenu(viewer, box, page).open();
                }
            });
            List<String> q = new ArrayList<>();
            int n = 1;
            for (Jukebox.Request r : box.queue()) q.add("<gray>" + (n++) + ". <white>" + MM.escapeTags(r.song().title));
            if (q.isEmpty()) q.add("<gray>Pusto.");
            if (viewer.hasPermission("szafa.admin") && !box.queue().isEmpty()) q.add("<yellow>Kliknij</yellow> <gray>— wyczyść i zatrzymaj (admin)");
            set(49, item(Material.WRITABLE_BOOK, "<#ffc02e><b>Kolejka</b>", q, false), e -> {
                if (viewer.hasPermission("szafa.admin")) {
                    box.stop();
                    new SongMenu(viewer, box, page).open();
                }
            });
            boolean m = isMuted(viewer.getUniqueId());
            set(50, item(m ? Material.GRAY_DYE : Material.NOTE_BLOCK, m ? "<gray><b>Muzyka wyciszona</b>" : "<#ffc02e><b>Muzyka włączona</b>",
                    List.of("<gray>Tylko dla ciebie, we wszystkich szafach.", "<yellow>Kliknij</yellow> <gray>— przełącz"), false), e -> {
                toggleMute(viewer);
                new SongMenu(viewer, box, page).open();
            });
        }

        private void request(Song s) {
            if (box.isQueuedOrPlaying(s)) {
                info(viewer, "<gray>Ten utwór już gra albo czeka w kolejce.");
                return;
            }
            if (!viewer.hasPermission("szafa.admin") && box.hasRequestFrom(viewer.getUniqueId())) {
                info(viewer, "<gray>Masz już utwór w kolejce. Poczekaj, aż zagra.");
                return;
            }
            if (box.queue().size() >= 8) {
                info(viewer, "<gray>Kolejka jest pełna (8). Spróbuj za chwilę.");
                return;
            }
            int posInQueue = box.enqueue(new Jukebox.Request(s, viewer.getUniqueId(), viewer.getName()));
            viewer.closeInventory();
            if (posInQueue > 0) info(viewer, "<gray>Dodano do kolejki: <white>" + MM.escapeTags(s.title) + "</white> (miejsce " + posInQueue + ").");
        }
    }

    private static ItemStack item(Material m, String name, List<String> lore, boolean glint) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(MM.deserialize(name).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        meta.lore(lore.stream().map(l -> MM.deserialize(l).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (glint) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Menu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != menu.inv) return;
        Consumer<InventoryClickEvent> b = menu.buttons.get(e.getSlot());
        if (b != null) {
            ((Player) e.getWhoClicked()).playSound(e.getWhoClicked(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
            b.accept(e);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase();
        boolean admin = s.hasPermission("szafa.admin");
        if (sub.equals("wycisz") && s instanceof Player p) {
            toggleMute(p);
            return true;
        }
        if (!admin) {
            s.sendMessage(MM.deserialize(PREFIX + "<gray>Kliknij szafę grającą PPM, żeby wybrać utwór. <white>/szafa wycisz</white> — wycisz muzykę."));
            return true;
        }
        switch (sub) {
            case "postaw", "usun" -> {
                if (!(s instanceof Player p)) return true;
                Block b = p.getTargetBlockExact(6);
                if (b == null) {
                    s.sendMessage(MM.deserialize(PREFIX + "<gray>Spójrz na blok (np. szafę grającą) z bliska."));
                    return true;
                }
                Jukebox existing = boxAt(b);
                if (sub.equals("postaw")) {
                    if (existing != null) {
                        s.sendMessage(MM.deserialize(PREFIX + "<gray>Tu już jest szafa."));
                        return true;
                    }
                    boxes.add(new Jukebox(this, b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
                    saveBoxes();
                    s.sendMessage(MM.deserialize(PREFIX + "<gray>Szafa postawiona. Za sekundę pojawi się napis nad nią."));
                } else {
                    if (existing == null) {
                        s.sendMessage(MM.deserialize(PREFIX + "<gray>Ten blok nie jest szafą."));
                        return true;
                    }
                    existing.remove();
                    boxes.remove(existing);
                    saveBoxes();
                    s.sendMessage(MM.deserialize(PREFIX + "<gray>Usunięto szafę (blok został)."));
                }
            }
            case "reload" -> {
                boxes.forEach(Jukebox::stop);
                List<String> errors = loadSongs();
                s.sendMessage(MM.deserialize(PREFIX + "<gray>Wczytano utworów: <white>" + songs.size()));
                for (String err : errors) s.sendMessage(MM.deserialize("<dark_gray> • " + MM.escapeTags(err)));
            }
            case "lista" -> {
                s.sendMessage(MM.deserialize(PREFIX + "<gray>Utwory (" + songs.size() + "), folder: <white>plugins/SunSzafa/utwory"));
                for (Song song : songs) {
                    s.sendMessage(MM.deserialize(" <dark_gray>•</dark_gray> <white>" + MM.escapeTags(song.title)
                            + "</white> <dark_gray>" + song.duration() + " · " + MM.escapeTags(song.id)));
                }
            }
            case "stop" -> {
                boxes.forEach(Jukebox::stop);
                s.sendMessage(MM.deserialize(PREFIX + "<gray>Zatrzymano wszystkie szafy."));
            }
            default -> {
                s.sendMessage(MM.deserialize(PREFIX + "<white>/szafa postaw</white> <gray>/ <white>usun</white> <gray>— patrząc na blok"));
                s.sendMessage(MM.deserialize(PREFIX + "<white>/szafa reload</white> <gray>— wczytaj nowe pliki .nbs z plugins/SunSzafa/utwory"));
                s.sendMessage(MM.deserialize(PREFIX + "<white>/szafa lista</white> <gray>· <white>/szafa stop</white> <gray>· <white>/szafa wycisz"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String label, String[] a) {
        if (a.length != 1) return List.of();
        Stream<String> opts = s.hasPermission("szafa.admin")
                ? Stream.of("postaw", "usun", "reload", "lista", "stop", "wycisz")
                : Stream.of("wycisz");
        return opts.filter(x -> x.startsWith(a[0].toLowerCase())).toList();
    }
}
