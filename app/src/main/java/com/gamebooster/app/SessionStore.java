package com.gamebooster.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Penyimpanan riwayat sesi bermain (file teks sederhana, tanpa database/library tambahan). */
public final class SessionStore {
    private SessionStore() {}

    private static final int MAX_FILES = 30;

    public static final class Sample {
        public final int t;       // detik sejak sesi mulai
        public final float temp;  // suhu baterai
        public final float ram;   // % RAM terpakai
        public final float cpu;   // suhu CPU (-1 jika tidak tersedia)
        public final int ping;    // ms (-1 jika tidak tersedia)
        public final float batt;  // % baterai (-1 jika tidak tersedia)

        Sample(int t, float temp, float ram, float cpu, int ping, float batt) {
            this.t = t;
            this.temp = temp;
            this.ram = ram;
            this.cpu = cpu;
            this.ping = ping;
            this.batt = batt;
        }
    }

    public static final class Session {
        public long start;
        public long end;
        public String pkg = "";
        public String label = "";
        public int mode = GameMode.BALANCED;
        public final List<Sample> samples = new ArrayList<>();

        public long durationSec() {
            long e;
            if (end > 0) e = end;
            else if (!samples.isEmpty()) e = start + samples.get(samples.size() - 1).t * 1000L;
            else e = start;
            return Math.max(0, (e - start) / 1000L);
        }
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "sessions");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File file(Context c, long start) {
        return new File(dir(c), start + ".txt");
    }

    private static long idOf(File f) {
        try {
            String n = f.getName();
            return Long.parseLong(n.substring(0, n.indexOf('.')));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static void append(File f, String line) {
        FileWriter w = null;
        try {
            w = new FileWriter(f, true);
            w.write(line);
            w.write("\n");
        } catch (IOException ignored) {
        } finally {
            try {
                if (w != null) w.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static synchronized void begin(Context c, long start, String pkg, String label, int mode) {
        if (start <= 0) return;
        String safe = label == null ? pkg : label.replace('|', ' ').replace('\n', ' ');
        append(file(c, start), "S|" + start + "|" + pkg + "|" + safe + "|" + mode);
        prune(c);
    }

    public static synchronized void addSample(Context c, long start, float temp, float ram, float cpu,
                                              int ping, float batt) {
        if (start <= 0) return;
        File f = file(c, start);
        if (!f.exists()) return;
        int t = (int) ((System.currentTimeMillis() - start) / 1000L);
        append(f, String.format(Locale.US, "P|%d|%.1f|%.1f|%.1f|%d|%.0f", t, temp, ram, cpu, ping, batt));
    }

    public static synchronized void end(Context c, long start) {
        if (start <= 0) return;
        File f = file(c, start);
        if (f.exists()) append(f, "E|" + System.currentTimeMillis());
    }

    /** Daftar sesi, terbaru lebih dulu. */
    public static synchronized List<Session> list(Context c) {
        List<Session> out = new ArrayList<>();
        File[] fs = dir(c).listFiles();
        if (fs == null) return out;
        Arrays.sort(fs, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(idOf(b), idOf(a));
            }
        });
        for (File f : fs) {
            Session s = parse(f);
            if (s != null) out.add(s);
        }
        return out;
    }

    public static synchronized void clear(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs == null) return;
        for (File f : fs) f.delete();
    }

    private static void prune(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs == null || fs.length <= MAX_FILES) return;
        List<File> l = new ArrayList<>(Arrays.asList(fs));
        Collections.sort(l, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(idOf(a), idOf(b));
            }
        });
        for (int i = 0; i < l.size() - MAX_FILES; i++) l.get(i).delete();
    }

    private static Session parse(File f) {
        BufferedReader br = null;
        Session s = null;
        try {
            br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|");
                if (p.length == 0) continue;
                try {
                    if ("S".equals(p[0]) && p.length >= 5) {
                        s = new Session();
                        s.start = Long.parseLong(p[1]);
                        s.pkg = p[2];
                        s.label = p[3];
                        s.mode = Integer.parseInt(p[4]);
                    } else if ("P".equals(p[0]) && p.length >= 5 && s != null) {
                        int ping = p.length >= 7 ? Integer.parseInt(p[5]) : -1;
                        float batt = p.length >= 7 ? Float.parseFloat(p[6]) : -1f;
                        s.samples.add(new Sample(Integer.parseInt(p[1]), Float.parseFloat(p[2]),
                                Float.parseFloat(p[3]), Float.parseFloat(p[4]), ping, batt));
                    } else if ("E".equals(p[0]) && p.length >= 2 && s != null) {
                        s.end = Long.parseLong(p[1]);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        } finally {
            try {
                if (br != null) br.close();
            } catch (IOException ignored) {
            }
        }
        return s;
    }
}
