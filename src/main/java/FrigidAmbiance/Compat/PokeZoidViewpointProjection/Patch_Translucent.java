package FrigidAmbiance.Compat.PokeZoidViewpointProjection;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "viewpoint.render.WorldRenderer", methodName = "drawTranslucent")
public final class Patch_Translucent {
    @Patch.OnExit
    public static void exit() { NativeBillboards.draw(); }
}
