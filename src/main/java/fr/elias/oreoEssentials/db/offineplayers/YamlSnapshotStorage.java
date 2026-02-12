package fr.elias.oreoEssentials.db.offineplayers;

import fr.elias.oreoEssentials.modules.enderchest.EnderSnapshot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class YamlSnapshotStorage implements SnapshotStorage {
    private final File dir;

    public YamlSnapshotStorage(JavaPlugin plugin) {
        this.dir = new File(plugin.getDataFolder(), "data");
        this.dir.mkdirs();
    }

    private File file(UUID id) { return new File(dir, id.toString() + ".yml"); }

    @Override public InvSnapshot loadInv(UUID id) {
        File f = file(id);
        if (!f.exists()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (!y.isSet("inv.contents")) return null;
        InvSnapshot s = new InvSnapshot();
        s.contents = ((List<ItemStack>) y.get("inv.contents", List.of())).toArray(new ItemStack[0]);
        s.armor    = ((List<ItemStack>) y.get("inv.armor", List.of())).toArray(new ItemStack[0]);
        s.offhand  = y.getItemStack("inv.offhand");
        return s;
    }

    @Override public void saveInv(UUID id, InvSnapshot s) {
        File f = file(id);
        YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        y.set("inv.contents", Arrays.asList(s.contents == null ? new ItemStack[0] : s.contents));
        y.set("inv.armor",    Arrays.asList(s.armor    == null ? new ItemStack[0] : s.armor));
        y.set("inv.offhand",  s.offhand);
        try { y.save(f); } catch (IOException e) { e.printStackTrace(); }
    }

    @Override public EnderSnapshot loadEnder(UUID id) {
        File f = file(id);
        if (!f.exists()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (!y.isSet("ender.items")) return null;
        EnderSnapshot s = new EnderSnapshot();
        s.chest = ((List<ItemStack>) y.get("ender.items", List.of())).toArray(new ItemStack[0]);
        return s;
    }

    @Override public void saveEnder(UUID id, EnderSnapshot s) {
        File f = file(id);
        YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        y.set("ender.items", Arrays.asList(s.chest == null ? new ItemStack[0] : s.chest));
        try { y.save(f); } catch (IOException e) { e.printStackTrace(); }
    }

    @Override public InvSnapshot loadPendingInv(UUID id)       { return loadInvField(id, "pending.inv"); }
    @Override public void savePendingInv(UUID id, InvSnapshot s){ saveInvField(id, "pending.inv", s); }
    @Override public void clearPendingInv(UUID id)              { clearField(id, "pending.inv"); }

    @Override public EnderSnapshot loadPendingEnder(UUID id)        { return loadEnderField(id, "pending.ender"); }
    @Override public void savePendingEnder(UUID id, EnderSnapshot s){ saveEnderField(id, "pending.ender", s); }
    @Override public void clearPendingEnder(UUID id)                { clearField(id, "pending.ender"); }

    // small helpers (same serialization as above) ...
    private InvSnapshot loadInvField(UUID id, String path) {
        File f = file(id); if (!f.exists()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (!y.isSet(path + ".contents")) return null;
        InvSnapshot s = new InvSnapshot();
        s.contents = ((List<ItemStack>) y.get(path + ".contents", List.of())).toArray(new ItemStack[0]);
        s.armor    = ((List<ItemStack>) y.get(path + ".armor", List.of())).toArray(new ItemStack[0]);
        s.offhand  = y.getItemStack(path + ".offhand");
        return s;
    }
    private void saveInvField(UUID id, String path, InvSnapshot s) {
        File f = file(id);
        YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        y.set(path + ".contents", Arrays.asList(s.contents == null ? new ItemStack[0] : s.contents));
        y.set(path + ".armor",    Arrays.asList(s.armor    == null ? new ItemStack[0] : s.armor));
        y.set(path + ".offhand",  s.offhand);
        try { y.save(f); } catch (IOException e) { e.printStackTrace(); }
    }
    private EnderSnapshot loadEnderField(UUID id, String path) {
        File f = file(id); if (!f.exists()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (!y.isSet(path + ".items")) return null;
        EnderSnapshot s = new EnderSnapshot();
        s.chest = ((List<ItemStack>) y.get(path + ".items", List.of())).toArray(new ItemStack[0]);
        return s;
    }
    private void saveEnderField(UUID id, String path, EnderSnapshot s) {
        File f = file(id);
        YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        y.set(path + ".items", Arrays.asList(s.chest == null ? new ItemStack[0] : s.chest));
        try { y.save(f); } catch (IOException e) { e.printStackTrace(); }
    }
    private void clearField(UUID id, String path) {
        File f = file(id); if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        y.set(path, null);
        try { y.save(f); } catch (IOException e) { e.printStackTrace(); }
    }
}
