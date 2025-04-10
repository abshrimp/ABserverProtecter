package net.ebii.ABserverProtecter;

import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Chest;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.block.Action;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class ABserverProtecter extends JavaPlugin implements Listener {

    // インタラクト可能なエンティティ
    private static final Set<Material> EXTRA_INTERACTABLE_BLOCKS = EnumSet.of(
            Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME, Material.MINECART
    );

    private final Set<UUID> targetedPlayers = new HashSet<>(); // UUIDを保存するセット

    private double xMin, xMax, yMin, yMax, zMin, zMax;

    @Override
    public void onDisable() {
        saveTargetedPlayers(); // プレイヤーリストの保存
    }

    // 保存メソッド
    private void saveTargetedPlayers() {
        FileConfiguration config = getConfig();
        // Set<UUID> を List<String> に変換して保存
        List<String> uuidList = targetedPlayers.stream().map(UUID::toString).collect(Collectors.toList());
        config.set("targetedPlayers", uuidList); // 明示的に List を設定
        try {
            saveConfig();
            getLogger().info("Config saved with: " + uuidList);
        } catch (Exception e) {
            getLogger().severe("Failed to save config: " + e.getMessage());
        }
    }

    // 読み込みメソッド
    private void loadTargetedPlayers() {
        FileConfiguration config = getConfig();
        targetedPlayers.clear(); // 既存データをクリア

        if (config.contains("targetedPlayers")) {
            // リスト形式の場合
            if (config.isList("targetedPlayers")) {
                List<String> uuidList = config.getStringList("targetedPlayers");
                for (String uuidString : uuidList) {
                    try {
                        targetedPlayers.add(UUID.fromString(uuidString));
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid UUID in config: " + uuidString);
                    }
                }
                getLogger().info("Loaded UUIDs as list: " + uuidList);
            }
            // !!set 形式の場合
            else if (config.isConfigurationSection("targetedPlayers")) {
                for (String key : config.getConfigurationSection("targetedPlayers").getKeys(false)) {
                    try {
                        targetedPlayers.add(UUID.fromString(key));
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid UUID in config: " + key);
                    }
                }
                getLogger().info("Loaded UUIDs as set: " + targetedPlayers);
            }
        }
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        loadTargetedPlayers();

        loadAllowArea(); // 保護エリアの設定を読み込む
        getLogger().info("ALLOW CHEST AREA:");
        getLogger().info("x_min: " + xMin + ", x_max: " + xMax);
        getLogger().info("y_min: " + yMin + ", y_max: " + yMax);
        getLogger().info("z_min: " + zMin + ", z_max: " + zMax);
        getLogger().info("<<<<< LOADED PROTECTER >>>>>");
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // 許可されていたら
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        if (targetedPlayers.contains(playerUUID)) {
            return;
        }

        Entity entity = event.getRightClicked();
        if (entity.getType().name().contains("BOAT")) {
            return;
        }

        player.sendMessage("操作する権限がありません");
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        // 許可されていたら
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        if (targetedPlayers.contains(playerUUID)) {
            return;
        }

        // アクションが右クリックでない場合は即座にキャンセル
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            player.sendMessage("建築する権限がありません");
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_AIR) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            // 手に持っているアイテムを取得
            ItemStack item = event.getItem();
            // 食べ物を持っている場合、食べるのを許可
            if (item != null && item.getType().isEdible()) {
                return;
            }
        }

        Block block = event.getClickedBlock();
        if (block != null) {
            // 手に持っているアイテムを取得
            ItemStack item = event.getItem();

            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !block.getType().isInteractable() && !EXTRA_INTERACTABLE_BLOCKS.contains(block.getType())) {
                // 食べ物を持っている場合、食べるのを許可
                if (item != null && item.getType().isEdible()) {
                    return;
                }
                // ボート関連のアイテムなら設置を許可
                if (item != null && item.getType().name().contains("BOAT")) {
                    return;
                }
            }

            // ベッドなら使用を許可
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block.getType().name().contains("BED") && !block.getType().name().equals("BEDROCK") && player.getWorld().getName().equals("world")) {
                return;
            }

            // チェストの場合、特定エリア内なら使用を許可
            if (block.getState() instanceof Chest) {
                Location loc = block.getLocation();
                if (isInAllowArea(loc)) {
                    return;
                }
            }

            if (block.getType().isInteractable() || EXTRA_INTERACTABLE_BLOCKS.contains(block.getType())) {
                player.sendMessage("操作する権限がありません");
            }
        }

        // 上記条件に当てはまらない場合、操作をキャンセル
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // エンティティがダメージを受けたときの処理

        // 攻撃者がプレイヤーかどうか確認
        if (event.getDamager() instanceof Player player) {
            // 許可されていたら
            UUID playerUUID = player.getUniqueId();
            if (targetedPlayers.contains(playerUUID)) {
                return;
            }

            Entity target = event.getEntity(); // 攻撃対象を取得

            // 敵対mobへのダメージのみを通す
            if (target instanceof Monster) {
                return;
            }

            player.sendMessage("ダメージを与える権限がありません");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        // 額縁が壊される場合に限定
        if (event.getEntity() instanceof ItemFrame) {
            // 壊したのがプレイヤーかどうか確認
            if (event.getRemover() instanceof Player player) {
                UUID playerUUID = player.getUniqueId();
                if (targetedPlayers.contains(playerUUID)) {
                    return;
                }

                player.sendMessage("壊す権限がありません");
                event.setCancelled(true); // 額縁の破壊をキャンセル
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // 爆発の原因がクリーパーかどうかを確認
        if (event.getEntity() instanceof Creeper) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        // 許可されていたら
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        if (targetedPlayers.contains(playerUUID)) {
            return;
        }
        event.setCancelled(true); // アイテムのドロップを禁止
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) { // 拾うのがプレイヤーか確認
            // 許可されていたら
            UUID playerUUID = event.getEntity().getUniqueId();
            if (targetedPlayers.contains(playerUUID)) {
                return;
            }
            event.setCancelled(true); // アイテムの拾うのを禁止
        }
    }

    // config.yml にデフォルト値を設定
    private void setupConfigDefaults() {
        FileConfiguration config = getConfig();
        config.addDefault("allowArea.x_min", 0.0);
        config.addDefault("allowArea.x_max", 100.0);
        config.addDefault("allowArea.y_min", 0.0);
        config.addDefault("allowArea.y_max", 256.0);
        config.addDefault("allowArea.z_min", 0.0);
        config.addDefault("allowArea.z_max", 100.0);
        config.options().copyDefaults(true); // デフォルト値を config.yml に書き込む
        saveConfig();
    }

    // 保護エリアの座標を読み込む
    private void loadAllowArea() {
        FileConfiguration config = getConfig();
        xMin = config.getDouble("allowArea.x_min");
        xMax = config.getDouble("allowArea.x_max");
        yMin = config.getDouble("allowArea.y_min");
        yMax = config.getDouble("allowArea.y_max");
        zMin = config.getDouble("allowArea.z_min");
        zMax = config.getDouble("allowArea.z_max");
    }

    // 保護エリアの座標を保存する
    private void saveAllowArea() {
        FileConfiguration config = getConfig();
        config.set("allowArea.x_min", xMin);
        config.set("allowArea.x_max", xMax);
        config.set("allowArea.y_min", yMin);
        config.set("allowArea.y_max", yMax);
        config.set("allowArea.z_min", zMin);
        config.set("allowArea.z_max", zMax);
        try {
            saveConfig();
            getLogger().info("Protected area saved successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to save protected area: " + e.getMessage());
        }
    }

    // プレイヤーが保護エリア内にいるかチェックするメソッド
    private boolean isInAllowArea(Location loc) {
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        return x >= xMin && x <= xMax &&
                y >= yMin && y <= yMax &&
                z >= zMin && z <= zMax;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("addMember")) {
            if (!sender.isOp()) {
                sender.sendMessage("このコマンドを実行する権限がありません。");
                return false;
            }

            if (args.length != 1) {
                sender.sendMessage("使用法: /addMember <プレイヤー名 または UUID>");
                return false;
            }

            String input = args[0];
            UUID targetUUID = null;

            // UUID形式かどうかチェック
            try {
                targetUUID = UUID.fromString(input);
            } catch (IllegalArgumentException e) {
                // UUIDでない場合はプレイヤー名とみなす
                Player targetPlayer = getServer().getPlayer(input);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    targetUUID = targetPlayer.getUniqueId();
                }
            }

            if (targetUUID != null) {
                targetedPlayers.add(targetUUID);
                sender.sendMessage(input + " はメンバーとして追加されました");
                saveTargetedPlayers();
            } else {
                sender.sendMessage("指定されたプレイヤーが存在しないか、オフラインです");
            }

            return true;
        }

        if (command.getName().equalsIgnoreCase("deleteMember")) {
            if (!sender.isOp()) {
                sender.sendMessage("このコマンドを実行する権限がありません。");
                return false;
            }

            if (args.length != 1) {
                sender.sendMessage("使用法: /deleteMember <プレイヤー名 または UUID>");
                return false;
            }

            String input = args[0];
            UUID targetUUID = null;

            // UUID形式かどうか判定
            try {
                targetUUID = UUID.fromString(input);
            } catch (IllegalArgumentException e) {
                // UUIDでない場合はプレイヤー名とみなす
                Player targetPlayer = getServer().getPlayer(input);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    targetUUID = targetPlayer.getUniqueId();
                }
            }

            if (targetUUID != null) {
                if (targetedPlayers.contains(targetUUID)) {
                    targetedPlayers.remove(targetUUID);
                    sender.sendMessage(input + " はメンバーから削除されました");
                    saveTargetedPlayers();
                } else {
                    sender.sendMessage(input + " はメンバーに存在しません");
                }
            } else {
                sender.sendMessage("指定されたプレイヤーが存在しないか、オフラインです");
            }

            return true;
        }

        if (command.getName().equalsIgnoreCase("setAllowArea")) {
            if (!sender.isOp()) {
                sender.sendMessage("このコマンドを実行する権限がありません。");
                return false;
            }

            if (args.length != 6) {
                sender.sendMessage("使用法: /setAllowArea <xMin> <xMax> <yMin> <yMax> <zMin> <zMax>");
                return false;
            }

            try {
                xMin = Double.parseDouble(args[0]);
                xMax = Double.parseDouble(args[1]);
                yMin = Double.parseDouble(args[2]);
                yMax = Double.parseDouble(args[3]);
                zMin = Double.parseDouble(args[4]);
                zMax = Double.parseDouble(args[5]);

                saveAllowArea(); // 保存
                sender.sendMessage("保護エリアを設定しました: " +
                        "xMin=" + xMin + ", xMax=" + xMax + ", " +
                        "yMin=" + yMin + ", yMax=" + yMax + ", " +
                        "zMin=" + zMin + ", zMax=" + zMax);
            } catch (NumberFormatException e) {
                sender.sendMessage("座標は数値で指定してください。");
            }
            return true;
        }

        return false;
    }
}
