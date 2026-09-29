package com.galaxymc.util;

/**
 * Procedural naming for stars, planets, minerals and creature strains.
 *
 * <p>Names are stitched from weighted syllable banks with a handful of phonotactic rules (no triple
 * consonants, occasional apostrophes and hyphens) so they read as alien but pronounceable. The same
 * seed always yields the same name, which is what lets two players on a server talk about "Vey'ra
 * Prime" and mean the same world.
 */
public final class NameGenerator {
    private NameGenerator() {}

    private static final String[] ONSETS = {
            "b", "br", "c", "ch", "d", "dr", "f", "g", "gh", "gl", "h", "j", "k", "kh", "kr", "l", "m", "n",
            "p", "ph", "qu", "r", "s", "sh", "sk", "st", "t", "th", "tr", "v", "vr", "x", "z", "zh", "y", "",
            "", "", "al", "or", "el", "ul"
    };
    private static final String[] NUCLEI = {
            "a", "e", "i", "o", "u", "a", "e", "o", "a", "e", "i", "o", "u", "y", "ae", "ai", "ei", "ou", "ia", "io"
    };
    private static final String[] CODAS = {
            "", "", "", "", "n", "r", "s", "th", "x", "l", "m", "k", "ss", "rn", "nd", "sh", "q", "z", "ll", "rk"
    };
    private static final String[] STAR_SUFFIX = {
            "", "", "", "", " Prime", " Major", " Minor", "'s Eye", " Reach", " Beacon", " Crown", " Hollow"
    };
    private static final String[] MINERAL_SUFFIX = {
            "ite", "ium", "ine", "on", "ar", "ide", "ose", "yx", "ite", "ium", "orite", "ane"
    };
    private static final String[] GREEK = {
            "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", "Iota", "Kappa", "Lambda", "Sigma", "Tau", "Omega"
    };
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** A single pronounceable word of the given syllable count. */
    public static String word(long seed, int syllables) {
        Hash.Rng rng = new Hash.Rng(seed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < syllables; i++) {
            String onset = rng.pick(ONSETS);
            if (i > 0 && onset.isEmpty()) {
                onset = rng.pick(new String[]{"r", "l", "n", "v", "th", "s", "k", "m"});
            }
            sb.append(onset).append(rng.pick(NUCLEI));
            if (i == syllables - 1 || rng.chance(0.35)) {
                sb.append(rng.pick(CODAS));
            }
            if (i < syllables - 1 && syllables > 2 && rng.chance(0.08)) {
                sb.append('\'');
            }
        }
        String w = sb.toString().replace("''", "'");
        w = collapse(w);
        if (w.length() < 3) {
            w = w + "ra";
        }
        return capitalize(w);
    }

    private static String collapse(String w) {
        StringBuilder out = new StringBuilder();
        int consonantRun = 0;
        for (char c : w.toCharArray()) {
            boolean vowel = "aeiouy'".indexOf(c) >= 0;
            consonantRun = vowel ? 0 : consonantRun + 1;
            if (consonantRun <= 2) {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String starName(long seed) {
        Hash.Rng rng = new Hash.Rng(seed ^ 0x5741524EL);
        int form = rng.nextInt(10);
        String base = word(rng.nextLong(), rng.chance(0.7) ? 2 : 3);
        return switch (form) {
            case 0 -> base + " " + rng.pick(GREEK);
            case 1 -> rng.pick(GREEK) + " " + base;
            case 2 -> base + "-" + (rng.nextInt(90) + 10);
            default -> base + rng.pick(STAR_SUFFIX);
        };
    }

    /** Planets either inherit the star name with a numeral or, sometimes, earn a name of their own. */
    public static String planetName(long seed, String starName, int orbit) {
        Hash.Rng rng = new Hash.Rng(seed ^ 0x504C414EL);
        if (rng.chance(0.4)) {
            String own = word(rng.nextLong(), rng.nextInt(2, 3));
            if (rng.chance(0.25)) {
                own = own + " " + rng.pick(new String[]{"Prime", "Secundus", "Minor", "Deep", "Verge", "Rest"});
            }
            return own;
        }
        return starName + " " + ROMAN[Math.max(0, Math.min(ROMAN.length - 1, orbit - 1))];
    }

    public static String mineralName(long seed) {
        Hash.Rng rng = new Hash.Rng(seed ^ 0x4D494E45L);
        String root = word(rng.nextLong(), rng.nextInt(1, 2)).toLowerCase();
        // Strip a trailing vowel so the suffix joins cleanly ("vora" + "ite" -> "vorite").
        while (root.length() > 3 && "aeiouy".indexOf(root.charAt(root.length() - 1)) >= 0) {
            root = root.substring(0, root.length() - 1);
        }
        return capitalize(root + rng.pick(MINERAL_SUFFIX));
    }

    public static String strainName(long seed) {
        return word(seed ^ 0x53545241L, 2) + "ian";
    }

    public static String roman(int n) {
        return ROMAN[Math.max(0, Math.min(ROMAN.length - 1, n - 1))];
    }

    /** HSV (all 0-1) to packed 0xRRGGBB. */
    public static int hsv(double h, double s, double v) {
        h = h - Math.floor(h);
        double r;
        double g;
        double b;
        int i = (int) Math.floor(h * 6.0);
        double f = h * 6.0 - i;
        double p = v * (1.0 - s);
        double q = v * (1.0 - f * s);
        double t = v * (1.0 - (1.0 - f) * s);
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
    }

    public static int mixColor(int a, int b, double t) {
        int ar = (a >> 16) & 255;
        int ag = (a >> 8) & 255;
        int ab = a & 255;
        int br = (b >> 16) & 255;
        int bg = (b >> 8) & 255;
        int bb = b & 255;
        int r = (int) Math.round(ar + (br - ar) * t);
        int g = (int) Math.round(ag + (bg - ag) * t);
        int bl = (int) Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
