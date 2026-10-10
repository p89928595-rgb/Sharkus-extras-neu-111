package sharkus.extras;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ersetzt Spielernamen überall dort, wo Minecraft Text zeichnet (Chat, Tab-Liste, Namensschilder ...). */
public final class Names {
    private static final Map<String, Pattern> CACHE = new HashMap<>();

    /** Wie oft der Text-Hook aufgerufen wurde (0 = Mixin nicht aktiv). */
    public static volatile int hookCalls = 0;

    private Names() { }

    private static boolean active(Config c) {
        if (!c.nametags) return false;
        boolean own = c.ownNameOverride != null && !c.ownNameOverride.isBlank();
        return own || (c.nameReplacements != null && !c.nameReplacements.isEmpty());
    }

    private static String replaceWord(String s, String from, String to) {
        if (from == null || from.isBlank()) return s;
        Pattern p = CACHE.computeIfAbsent(from, k -> Pattern.compile(
                "(?<![A-Za-z0-9_])" + Pattern.quote(k) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE));
        return p.matcher(s).replaceAll(Matcher.quoteReplacement(to));
    }

    public static String apply(String s) {
        Config c = Config.I;
        if (s == null || s.length() < 2 || !active(c)) return s;
        String out = s;
        Minecraft mc = Minecraft.getInstance();
        if (c.ownNameOverride != null && !c.ownNameOverride.isBlank() && mc.player != null) {
            out = replaceWord(out, mc.player.getName().getString(), c.ownNameOverride.trim());
        }
        if (c.nameReplacements != null) {
            for (Map.Entry<String, String> e : new ArrayList<>(c.nameReplacements.entrySet())) {
                out = replaceWord(out, e.getKey(), e.getValue());
            }
        }
        return out;
    }

    public static FormattedText apply(FormattedText t) {
        Config c = Config.I;
        if (t == null || !active(c)) return t;
        List<FormattedText> parts = new ArrayList<>();
        boolean[] changed = {false};
        t.visit((style, str) -> {
            String n = apply(str);
            if (!n.equals(str)) changed[0] = true;
            parts.add(FormattedText.of(n, style));
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? FormattedText.composite(parts) : t;
    }

    // Schutz gegen Endlos-Rekursion im Mixin
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);

    public static boolean busy() {
        return BUSY.get();
    }

    public static void setBusy(boolean b) {
        BUSY.set(b);
    }
}
