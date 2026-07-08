package thennx.mcx86.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import thennx.mcx86.Config;
import thennx.mcx86.MCx86Mod;

public class ConfigScreen extends Screen {
    private final Screen lastScreen;

    private abstract class ValueWidget<T> {
        protected final EditBox editBox;
        protected final String name;
        protected final ForgeConfigSpec.ConfigValue<T> configValue;

        public ValueWidget(int y, ForgeConfigSpec.ConfigValue<T> configValue, String name) {
            editBox = new EditBox(font, width / 4, 2 + y, width - width / 4 - 25, 20, Component.empty());
            this.configValue = configValue;
            this.name = name;

            editBox.setFilter(s -> {
                try {
                    getTFromString(s);
                }
                catch (Exception e) {
                    return false;
                }
                return true;
            });

            editBox.setValue(getStringFromT(configValue.get()));

            addRenderableWidget(editBox);
        }

        protected void render(GuiGraphics graphics) {
            graphics.drawString(font, Component.translatable(String.format("gui.%s.label.%s", MCx86Mod.MODID, name)), 5, editBox.getY() + 5, 16777215);
        }

        public abstract T getTFromString(String s);
        public abstract String getStringFromT(T t);

        public void saveConfig() {
            configValue.set(getTFromString(editBox.getValue()));
            configValue.save();
        }
    }

    public class IntegerValueWidget extends ValueWidget<Integer> {
        public IntegerValueWidget(int y, ForgeConfigSpec.IntValue configValue, String name) {
            super(y, configValue, name);
        }

        @Override
        public Integer getTFromString(String s) {
            return Integer.parseInt(s);
        }

        @Override
        public String getStringFromT(Integer integer) {
            return String.valueOf(integer);
        }
    }

    private IntegerValueWidget emulationWorkers;
    private IntegerValueWidget emulationQuantum;

    public ConfigScreen(Screen lastScreen) {
        super(Component.literal("MCx86 Config"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        emulationWorkers = new IntegerValueWidget(30, Config.EMULATION_WORKERS, "emulation_workers");
        emulationQuantum = new IntegerValueWidget(60, Config.EMULATION_QUANTUM, "emulation_quantum");

        this.addRenderableWidget(Button.builder(Component.literal("Confirm"), button -> {
            saveConfig();
            this.minecraft.setScreen(lastScreen);
        }).bounds(this.width / 2 - 100, this.height / 2 + 10, 200, 20).build());
    }

    private void saveConfig() {
        emulationWorkers.saveConfig();
        emulationQuantum.saveConfig();

        Config.applyPoolSettings();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 16777215);
        super.render(graphics, mouseX, mouseY, partialTick);

        emulationQuantum.render(graphics);
        emulationWorkers.render(graphics);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(lastScreen);
    }
}
