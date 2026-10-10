package sharkus.extras;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class SharkusExtras implements ClientModInitializer {
    public static KeyMapping OPEN_MENU;

    @Override
    public void onInitializeClient() {
        Config.load();

        // GLFW kennt kein "Ü" - die Taste liegt auf deutschem Layout auf LEFT_BRACKET
        OPEN_MENU = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.sharkus-extras.menu", GLFW.GLFW_KEY_LEFT_BRACKET,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sharkus-extras", "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_MENU.consumeClick()) mc.gui.setScreen(new MenuScreen(null));
            Hud.tick(mc);
        });

        Hud.register();
        WorldFx.register();
    }
}
