package FrigidAmbiance.Compat.PokeZoidViewpointProjection;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.gameStates.IngameState", methodName = "update")
public final class Patch_IngameUpdate {
    @Patch.OnExit
    public static void exit() {
        Main.install();
    }
}
