package fr.elias.oreoEssentials.util;

public final class TimeText {
    private TimeText(){}

    public static long parseToMillis(String s) {
        if (s == null || s.isBlank()) return 0L;
        s = s.trim().toLowerCase();
        if (s.equals("perm") || s.equals("permanent")) return 0L;
        long num = 0; char unit = 's';
        try {
            int i = 0;
            while (i < s.length() && (Character.isDigit(s.charAt(i)))) i++;
            num = Long.parseLong(s.substring(0, i));
            if (i < s.length()) unit = s.charAt(i);
        } catch (Exception ignored) { return 0L; }
        return switch (unit) {
            case 's' -> num * 1000L;
            case 'm' -> num * 60_000L;
            case 'h' -> num * 3_600_000L;
            case 'd' -> num * 86_400_000L;
            default -> num * 1000L;
        };
    }

    public static String format(long ms) {
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append("d ");
        if (h > 0) b.append(h).append("h ");
        if (m > 0) b.append(m).append("m ");
        if (s > 0 || b.length()==0) b.append(s).append("s");
        return b.toString().trim();
    }
}
