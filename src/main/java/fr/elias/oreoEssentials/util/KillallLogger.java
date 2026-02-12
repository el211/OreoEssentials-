package fr.elias.oreoEssentials.util;

import fr.elias.oreoEssentials.OreoEssentials;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class KillallLogger {

    public record Record(Date when, String actor, String world, int x, int y, int z,
                         double radius, String type, int count, String reason) {}

    private final OreoEssentials plugin;
    private final File file;
    private final ReentrantLock lock = new ReentrantLock();
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public KillallLogger(OreoEssentials plugin) {
        this.plugin = plugin;
        File folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("killall.log-folder", "logs"));
        if (!folder.exists()) folder.mkdirs();
        this.file = new File(folder, plugin.getConfig().getString("killall.log-file", "killall-log.csv"));
        ensureHeader();
    }

    private void ensureHeader() {
        if (file.exists()) return;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            pw.println("timestamp,actor,world,x,y,z,radius,type,count,reason");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create killall log header: " + e.getMessage());
        }
    }

    public void append(Record r) {
        String line = String.join(",",
                csv(df.format(r.when())),
                csv(r.actor()),
                csv(r.world()),
                String.valueOf(r.x()),
                String.valueOf(r.y()),
                String.valueOf(r.z()),
                String.format(Locale.US,"%.1f", r.radius()),
                csv(r.type()),
                String.valueOf(r.count()),
                csv(r.reason() == null ? "" : r.reason())
        );
        lock.lock();
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            pw.println(line);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write killall record: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        String t = s.replace("\"","\"\"");
        return "\"" + t + "\"";
    }

    public List<String> tail(int limit) {
        List<String> all = new ArrayList<>();
        if (!file.exists()) return all;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) all.add(line);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read killall log: " + e.getMessage());
        }
        int start = Math.max(1, all.size() - limit);
        return all.subList(start, all.size());
    }
}
