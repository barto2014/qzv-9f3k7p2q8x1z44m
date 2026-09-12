package com.bartotv.tv;

// Fuente de datos: tu repo GitHub (datar.bin en base64, mismo formato que la web).
// Cambiá OWNER/REPO si movés los datos al repo oculto.
public final class Config {
    public static final String OWNER = "barto2014";
    public static final String REPO = "qzv-9f3k7p2q8x1z44m";
    public static final String BRANCH = "main";
    public static final String PATH = "datar.bin";

    public static String datarUrl() {
        return "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/" + BRANCH + "/" + PATH;
    }

    private Config() {}
}
