package FrigidAmbiance.Compat.PokeZoidViewpointProjection;

import java.lang.reflect.Field;
import java.io.File;
import zombie.characters.IsoPlayer;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.Lua.LuaManager;

public final class Main {
    private static Object installedEnv;
    private static Object scriptPlayer;
    private static Object scriptEnv;
    private static boolean scriptsLoading;

    private static boolean warned;
    private static long nextProjectionDiagnostic;

    private static void diagnostic(String message) {
        long now = System.nanoTime();
        if (now < nextProjectionDiagnostic) return;
        nextProjectionDiagnostic = now + 5_000_000_000L;
        System.out.println("[PokeZoidViewpoint] projection: " + message);
    }
    private static Field frameField;
    private static final float Z_SCALE = 2.4494896f;

    public static void main(String[] args) {
        System.out.println("[PokeZoidViewpoint] Java projection bridge loaded");
        install();
    }

    public static void install() {
        if (LuaManager.env == null) return;
        if (installedEnv == LuaManager.env) {
            ensureClientScripts();
            return;
        }

        try {
            LuaManager.env.rawset("PZVPProject", new Project());
            LuaManager.env.rawset("PZVPSubmit", new NativeBillboards.Submit());
            LuaManager.env.rawset("PZVPBegin", new NativeBillboards.Begin());
            LuaManager.env.rawset("PZVPNativeReady", new NativeBillboards.Ready());

            installedEnv = LuaManager.env;
            NativeBillboards.clear();

            System.out.println(
                "[PokeZoidViewpoint] PZVPProject Lua function registered"
            );
            ensureClientScripts();
        } catch (Throwable ex) {
            System.out.println(
                "[PokeZoidViewpoint] Lua registration failed: " + ex
            );
        }
    }

    private static void ensureClientScripts() {
        if (scriptsLoading || LuaManager.env == null) return;
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null) return;
        if (scriptPlayer == player && scriptEnv == LuaManager.env) return;

        scriptsLoading = true;
        try {
            File jar = new File(Main.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            File root = jar.getParentFile().getParentFile().getParentFile();
            File lua = new File(root, "media/lua/client/PokeZoidViewpoint");
            if (!lua.isDirectory()) {
                System.out.println("[PokeZoidViewpoint] client Lua folder not found: " + lua);
                return;
            }
            for (String filename : new String[] {
                "Bridge.lua", "BillboardOverlay.lua"
            }) {
                File file = new File(lua, filename);
                if (!file.isFile()) throw new java.io.FileNotFoundException(file.toString());
                LuaManager.RunLua(file.getAbsolutePath());
            }
            scriptPlayer = player;
            scriptEnv = LuaManager.env;
            NativeBillboards.clear();
            System.out.println("[PokeZoidViewpoint] client Lua reattached for player session");
        } catch (Throwable ex) {
            System.out.println("[PokeZoidViewpoint] client Lua reattach failed: " + ex);
        } finally {
            scriptsLoading = false;
        }
    }

    private static final class Project implements JavaFunction {
        @Override public int call(LuaCallFrame stack, int count) {
            try {
                if (count < 6) {
                    diagnostic("wrong argument count=" + count);
                    return 0;
                }
                double x = num(stack.get(0));
                double y = num(stack.get(1));
                double z = num(stack.get(2));
                double offset = num(stack.get(3));
                float width = (float) num(stack.get(4));
                float height = (float) num(stack.get(5));
                if (!(width > 0 && height > 0)) {
                    diagnostic("invalid viewport=" + width + "x" + height);
                    return 0;
                }
                float[] position = project(x, y, z, offset, width, height);
                if (position == null) return 0;
                stack.push((double) position[0], (double) position[1]);
                stack.push((double) position[2], (double) position[3]);
                return 4;
            } catch (Throwable ex) {
                diagnostic("exception: " + ex.getClass().getName() + ": " + ex.getMessage());
                if (!warned) {
                    warned = true;
                    ex.printStackTrace(System.out);
                }
                return 0;
            }
        }
    }

    private static double num(Object value) {
        if (!(value instanceof Number)) throw new IllegalArgumentException("expected number");
        return ((Number)value).doubleValue();
    }

    private static Object field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getField(name);
        return f.get(owner);
    }

    private static float[] project(double x, double y, double z, double offset, float width, float height) throws Exception {
        if (frameField == null) {
            Class<?> renderer = Class.forName("viewpoint.render.WorldRenderer");
            frameField = renderer.getDeclaredField("frame");
            frameField.setAccessible(true);
        }
        Object ctx = frameField.get(null);
        if (ctx == null) {
            diagnostic("WorldRenderer.frame is null");
            return null;
        }
        Object scene = field(ctx, "scene");
        Object matrix = field(ctx, "viewProjection");
        if (scene == null || matrix == null) {
            diagnostic("frame incomplete: scene=" + (scene != null)
                + " matrix=" + (matrix != null));
            return null;
        }
        double ox = num(field(scene, "originX"));
        double oy = num(field(scene, "originY"));
        double oz = num(field(scene, "originZ"));
        float sx = (float) (-x - ox);
        float sy = (float) (z * Z_SCALE - oy + offset);
        float sz = (float) (-y - oz);

        Class<?> v4 = Class.forName("org.joml.Vector4f");
        Object clip = v4.getConstructor().newInstance();
        matrix.getClass().getMethod("transform", float.class, float.class, float.class, float.class, v4)
            .invoke(matrix, sx, sy, sz, 1f, clip);
        float cw = ((Number)field(clip, "w")).floatValue();
        if (cw <= 0.05f || !Float.isFinite(cw)) {
            diagnostic(String.format(java.util.Locale.ROOT,
                "rejected depth: clipW=%.4f world=(%.2f,%.2f,%.2f) origin=(%.2f,%.2f,%.2f) relative=(%.2f,%.2f,%.2f)",
                cw, x, y, z, ox, oy, oz, sx, sy, sz));
            return null;
        }
        float nx = ((Number)field(clip, "x")).floatValue() / cw;
        float ny = ((Number)field(clip, "y")).floatValue() / cw;
        if (!Float.isFinite(nx) || !Float.isFinite(ny)) {
            diagnostic("non-finite NDC x=" + nx + " y=" + ny + " clipW=" + cw);
            return null;
        }
        Object panini = field(ctx, "panini");
        if (panini != null) {
            float[] out = new float[2];
            panini.getClass().getMethod("toScreen", float.class, float.class, float[].class)
                .invoke(panini, nx, ny, out);
            nx = out[0]; ny = out[1];
        }
        float screenX = (0.5f + 0.5f * nx)*width;
        float screenY = (0.5f - 0.5f * ny)*height;
        diagnostic(String.format(java.util.Locale.ROOT,
            "accepted: world=(%.2f,%.2f,%.2f) screen=(%.1f,%.1f) depth=%.3f viewport=%.0fx%.0f",
            x, y, z, screenX, screenY, cw, width, height));
        float clipZ = ((Number)field(clip, "z")).floatValue();
        return new float[] {screenX, screenY, cw, clipZ / cw};
    }
}
