package FrigidAmbiance.Compat.PokeZoidViewpointProjection;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(
    className = "zombie.gameStates.IngameState",
    methodName = "enter"
)
public final class Patch_PlayStarted {

    @Patch.OnExit
    public static void exit() {
        Main.install();
    }
}

