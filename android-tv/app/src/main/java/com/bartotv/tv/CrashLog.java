package com.bartotv.tv;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

// Guarda el crash en un archivo y lo muestra al reabrir. Sin adb.
public final class CrashLog {
    private static final String NAME = "crash.txt";

    public static void init(Context ctx) {
        Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(bos);
                e.printStackTrace(ps);
                ps.flush();
                FileOutputStream fos = ctx.openFileOutput(NAME, Context.MODE_PRIVATE);
                String head = new java.util.Date().toString() + "\n";
                fos.write(head.getBytes(StandardCharsets.UTF_8));
                fos.write(bos.toByteArray());
                fos.close();
            } catch (Exception ignored) {}
            if (old != null) old.uncaughtException(t, e);
            else System.exit(1);
        });
    }

    public static String readPrevious(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), NAME);
            if (!f.exists()) return null;
            FileInputStream fis = ctx.openFileInput(NAME);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            fis.close();
            String s = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            // Limpiar para no mostrarlo siempre
            ctx.deleteFile(NAME);
            // Recortar a lo útil (primeras 40 líneas)
            String[] lines = s.split("\n");
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < Math.min(lines.length, 40); i++) out.append(lines[i]).append('\n');
            return out.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private CrashLog() {}
}
