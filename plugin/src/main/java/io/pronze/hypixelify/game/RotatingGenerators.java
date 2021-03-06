package io.pronze.hypixelify.game;

import io.pronze.hypixelify.SBAHypixelify;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.game.ItemSpawner;
import org.screamingsandals.bedwars.lib.nms.holograms.Hologram;
import java.util.ArrayList;
import java.util.List;

public class RotatingGenerators implements io.pronze.hypixelify.api.game.RotatingGenerators {

    public static final String entityName = "sba_rot_entity";
    public static List<RotatingGenerators> cache = new ArrayList<>();
    @Getter private List<String> lines;
    private ArmorStand armorStand;
    private Location location;
    private ItemStack itemStack;
    @Getter private Hologram hologram;
    private final ItemSpawner itemSpawner;
    private int time;

    public RotatingGenerators(ItemSpawner spawner,
                              ItemStack itemStack,
                              List<String> lines) {
        this.location = spawner.getLocation();
        this.itemStack = itemStack;
        this.lines = lines;
        this.itemSpawner = spawner;
        time = spawner.getItemSpawnerType().getInterval() + 1;
        cache.add(this);
    }

    public static void scheduleTask() {

        Bukkit.getScheduler().runTaskTimer(SBAHypixelify.getInstance(), () -> {
            cache.stream().filter(generator-> generator != null &&
                                 generator.location != null &&
                                 generator.armorStand != null).forEach(generator -> {
                final Location loc = generator.location;
                loc.setYaw(loc.getYaw() + 10f);
                generator.armorStand.teleport(loc);
                generator.location = loc;
            });
        }, 0L, 2L);

        //Maybe use bedwarsgametickevent for this?
        Bukkit.getScheduler().runTaskTimer(SBAHypixelify.getInstance(), () -> {
            cache.stream().filter(generator-> generator != null && generator.hologram != null)
                    .forEach(generator -> {
                generator.time--;


                final var lines = generator.getLines();
                if (lines != null) {
                    final var newLines = new ArrayList<String>();

                    for (var line : lines) {
                        if (line == null) {
                            continue;
                        }
                        newLines.add(line.replace("{seconds}", String.valueOf(generator.time)));
                    }

                    generator.update(newLines);
                }

                if (generator.time <= 0) {
                    generator.time = generator.itemSpawner.getItemSpawnerType().getInterval();
                }
            });
        }, 0L, 20L);
    }

    public static void destroy(List<RotatingGenerators> rotatingGenerators) {
        if (rotatingGenerators == null || rotatingGenerators.isEmpty()) {
            return;
        }

        rotatingGenerators.forEach(generator -> {
            if (generator == null) {
                return;
            }

            generator.destroy();
        });

        cache.removeAll(rotatingGenerators);

    }

    public ArmorStand getArmorStandEntity() {
        return armorStand;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public RotatingGenerators spawn(List<Player> players) {
        final var holoHeight = (float) SBAHypixelify.getConfigurator()
                .config.getDouble("floating-generator.holo-height", 2.0);

        final var itemHeight = (float) SBAHypixelify.getConfigurator()
                .config.getDouble("floating-generator.item-height", 0.25);

        hologram = Main.getHologramManager()
                .spawnHologram(players, location.clone().add(0, holoHeight, 0), lines.toArray(new String[0]));

        armorStand = (ArmorStand) location.getWorld().
                spawnEntity(location.clone().add(0, itemHeight, 0), EntityType.ARMOR_STAND);
        armorStand.setCustomName(entityName);
        armorStand.setVisible(false);
        armorStand.setHelmet(itemStack);
        armorStand.setGravity(false);
        return this;
    }

    public void update(List<String> lines) {
        if (lines == null || lines.size() < 1) {
            return;
        }

        if (this.lines.equals(lines)) {
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) == null) {
                continue;
            }
            hologram.setLine(i, lines.get(i));
        }

        this.lines = new ArrayList<>(lines);

    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public ItemSpawner getItemSpawner() {
        return itemSpawner;
    }

    public void setLine(int index, String line) {
        hologram.setLine(index, line);
        if (lines != null) {
            lines.set(index, line);
        }
    }

    public void destroy() {
        if (armorStand != null)
            armorStand.remove();
        if (hologram != null)
            hologram.destroy();
    }


}
