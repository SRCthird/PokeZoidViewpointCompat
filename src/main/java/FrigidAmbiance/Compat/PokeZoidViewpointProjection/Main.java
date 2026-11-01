package FrigidAmbiance.Compat.PokeZoidViewpointProjection;

import zombie.Lua.LuaManager;

public final class Main {
    private static boolean installed;

    public static void main(String[] args) {
        System.out.println("[PokeZoidViewpoint] Java projection bridge loaded");
        install();
    }

    public static void install() {
        if (installed || LuaManager.env == null) return;

        try {
            installed = true;

            System.out.println(
                "[PokeZoidViewpoint] Lua function registered"
            );
        } catch (Throwable ex) {
            System.out.println(
                "[PokeZoidViewpoint] Lua registration failed: " + ex
            );
        }
    }

}

