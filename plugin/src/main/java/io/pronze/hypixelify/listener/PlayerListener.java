package io.pronze.hypixelify.listener;

import io.pronze.hypixelify.SBAHypixelify;
import io.pronze.hypixelify.game.Arena;
import io.pronze.hypixelify.game.PlayerData;
import io.pronze.hypixelify.game.RotatingGenerators;
import io.pronze.hypixelify.message.Messages;
import io.pronze.hypixelify.service.PlayerWrapperService;
import io.pronze.hypixelify.utils.SBAUtil;
import io.pronze.hypixelify.utils.ShopUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.BedwarsAPI;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.game.CurrentTeam;
import org.screamingsandals.bedwars.game.GamePlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.screamingsandals.bedwars.lib.nms.title.Title.sendTitle;

public class PlayerListener implements Listener {


    private final List<Material> allowed;
    private final List<Material> generatorDropItems;
    private final boolean giveKillerResources, respawnCooldown, disableArmorInventoryMovement,
            disableArmorDamage, permanentItems, blockItemOnChest, blockItemDrops;



    public PlayerListener() {
        FileConfiguration config = SBAHypixelify.getConfigurator().config;

        blockItemDrops = config.getBoolean("block-item-drops", true);
        giveKillerResources = config.getBoolean("give-killer-resources", true);
        respawnCooldown = config.getBoolean("respawn-cooldown.enabled", true);
        disableArmorInventoryMovement = config.getBoolean("disable-armor-inventory-movement", true);
        disableArmorDamage = config.getBoolean("disable-sword-armor-damage", true);
        permanentItems = config.getBoolean("permanent-items", true);
        blockItemOnChest = config.getBoolean("block-players-putting-certain-items-onto-chest", true);

        allowed = SBAUtil.parseMaterialFromConfig("allowed-item-drops");
        generatorDropItems = SBAUtil.parseMaterialFromConfig("running-generator-drops");

    }


    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        SBAHypixelify.debug("Player death event called");

        final Player player = e.getEntity();

        final Game game = BedwarsAPI.getInstance().getGameOfPlayer(player);
        if (game == null || game.getStatus() != GameStatus.RUNNING) return;

        final Arena arena = SBAHypixelify.getArena(game.getName());

        if (arena == null) {
            SBAHypixelify.debug("Arena is null");
            return;
        }

        Bukkit.getScheduler().runTaskLater(SBAHypixelify.getInstance(), () -> {
            if (arena.getScoreboard() != null) {
                arena.getScoreboard().updateScoreboard();
            }
        }, 1L);

        final List<ItemStack> itemArr = new ArrayList<>();
        if (permanentItems) {
            final ItemStack sword = Main.isLegacy() ? new ItemStack(Material.valueOf("WOOD_SWORD")) : new ItemStack(Material.WOODEN_SWORD);


            Arrays.stream(player.getInventory().getContents().clone()).forEach(stack -> {
                if (stack == null) {
                    return;
                }
                final String name = stack.getType().name();

                if (name.endsWith("SWORD"))
                    sword.addEnchantments(stack.getEnchantments());

                if (name.endsWith("AXE"))
                    itemArr.add(ShopUtil.checkifUpgraded(stack));

                if (    name.endsWith("LEGGINGS") ||
                        name.endsWith("BOOTS") ||
                        name.endsWith("CHESTPLATE") ||
                        name.endsWith("HELMET"))
                    itemArr.add(stack);

                if (name.contains("SHEARS")) {
                    itemArr.add(stack);
                }

                SBAHypixelify.debug(stack.getType().name());
            });

            itemArr.add(sword);
            PlayerData playerData = arena.getPlayerData(player.getUniqueId());
            playerData.setInventory(itemArr);
        }


        if (giveKillerResources) {
            Player killer = e.getEntity().getKiller();

            if (killer != null && BedwarsAPI.getInstance().isPlayerPlayingAnyGame(killer)
                    && killer.getGameMode() == GameMode.SURVIVAL) {
                for (ItemStack drop : player.getInventory().getContents().clone()) {
                    if (drop == null) {
                        continue;
                    }

                    if (generatorDropItems.contains(drop.getType())) {
                        killer.sendMessage("+" + drop.getAmount() + " " + drop.getType().name());
                        killer.getInventory().addItem(drop);
                    }
                }
            }
        }

        GamePlayer gVictim = Main.getPlayerGameProfile(player);

        CurrentTeam victimTeam = Main.getGame(game.getName()).getPlayerTeam(gVictim);

        if (respawnCooldown && victimTeam.isAlive() && game.isPlayerInAnyTeam(player) &&
                game.getTeamOfPlayer(player).isTargetBlockExists()) {

            new BukkitRunnable() {
                final GamePlayer gamePlayer = gVictim;
                final Player player = gamePlayer.player;
                int livingTime = SBAHypixelify.getConfigurator().config.getInt("respawn-cooldown.time", 5);

                byte buffer = 2;

                @Override
                public void run() {
                    if (!BedwarsAPI.getInstance().isPlayerPlayingAnyGame(player)) {
                        this.cancel();
                        return;
                    }

                    //send custom title because we disabled Bedwars from showing any title
                    if (livingTime > 0) {
                        sendTitle(player, Messages.message_respawn_title,
                                Messages.message_respawn_subtitle.replace("%time%", String.valueOf(livingTime)), 0, 20, 0);
                        player.sendMessage(Messages.message_respawn_subtitle.replace("%time%", String.valueOf(livingTime)));
                        livingTime--;
                    }


                    if (livingTime == 0) {
                        if (gVictim.isSpectator && buffer > 0) {
                            buffer--;
                        } else {
                            player.sendMessage(Messages.message_respawned_title);
                            sendTitle(player, "§aRESPAWNED!", "", 5, 40, 5);
                            ShopUtil.giveItemToPlayer(itemArr, player, Main.getGame(game.getName()).getPlayerTeam(gamePlayer).getColor());
                            this.cancel();
                        }
                    }
                }
            }.runTaskTimer(SBAHypixelify.getInstance(), 0L, 20L);
        }
    }


    @EventHandler
    public void onPlayerArmorStandManipulateEvent (PlayerArmorStandManipulateEvent  e) {
         final ArmorStand armorStand = e.getRightClicked();

         if (armorStand.getCustomName() != null && armorStand.getCustomName().equalsIgnoreCase(RotatingGenerators.entityName)) {
             e.setCancelled(true);
         }
    }


    @EventHandler(priority = EventPriority.NORMAL)
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null)
            return;

        if (!(event.getWhoClicked() instanceof Player)) return;

        final Player player = (Player) event.getWhoClicked();

        if (!BedwarsAPI.getInstance().isPlayerPlayingAnyGame(player)) return;

        if (disableArmorInventoryMovement && event.getSlotType() == SlotType.ARMOR)
            event.setCancelled(true);

        final Inventory topSlot = event.getView().getTopInventory();
        final Inventory bottomSlot = event.getView().getBottomInventory();
        final Inventory clickedInventory = event.getClickedInventory();
        final String typeName = event.getCurrentItem().getType().name();

        if (clickedInventory == null) return;

        if (clickedInventory.equals(bottomSlot) && blockItemOnChest &&
                (topSlot.getType() == InventoryType.CHEST || topSlot.getType() == InventoryType.ENDER_CHEST) &&
                bottomSlot.getType() == InventoryType.PLAYER) {
            if (typeName.endsWith("AXE") || typeName.endsWith("SWORD")) {
                event.setResult(Event.Result.DENY);
                player.sendMessage("§c§l" + SBAHypixelify.getConfigurator().getString("message.cannot-put-item-on-chest"));
            }
        }
    }


    @EventHandler
    public void onItemDrop(PlayerDropItemEvent evt) {
        if (!BedwarsAPI.getInstance().isPlayerPlayingAnyGame(evt.getPlayer())) return;
        if (!blockItemDrops) return;

        final Player player = evt.getPlayer();
        final ItemStack ItemDrop = evt.getItemDrop().getItemStack();
        final Material type = ItemDrop.getType();

        if (!allowed.contains(type) && !type.name().endsWith("WOOL")) {
            evt.setCancelled(true);
            player.getInventory().remove(ItemDrop);
        }
    }


    @EventHandler
    public void itemDamage(PlayerItemDamageEvent e) {
        if (!disableArmorDamage) return;
        Player player = e.getPlayer();
        if (!BedwarsAPI.getInstance().isPlayerPlayingAnyGame(player)) return;

        if (Main.getPlayerGameProfile(player).isSpectator) return;

        final String typeName = e.getItem().getType().toString();

        if (typeName.contains("BOOTS")
                || typeName.contains("HELMET")
                || typeName.contains("LEGGINGS")
                || typeName.contains("CHESTPLATE")
                || typeName.contains("SWORD")) {
            e.setCancelled(true);
        }

    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        final Player player = e.getPlayer();
        final PlayerWrapperService dbManager = SBAHypixelify.getWrapperService();
        dbManager.handleOffline(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        final Player player = e.getPlayer();
        SBAHypixelify.getWrapperService().register(player);

        if (!player.isOp())
            return;

        if (!SBAHypixelify.getConfigurator().getString("version", SBAHypixelify.getVersion())
                .contains(SBAHypixelify.getVersion())) {
            Bukkit.getScheduler().runTaskLater(SBAHypixelify.getInstance(), () -> {
                player.sendMessage("§6[SBAHypixelify]: Plugin has detected a version change, do you want to upgrade internal files?");
                player.sendMessage("Type /bwaddon upgrade to upgrade file");
                player.sendMessage("§cif you want to cancel the upgrade files do /bwaddon cancel");
            }, 40L);
        }
    }


}
