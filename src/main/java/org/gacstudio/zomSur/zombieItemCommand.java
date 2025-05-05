package org.gacstudio.zomSur;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.bukkit.Bukkit.getServer;

public class zombieItemCommand implements CommandExecutor, Listener {

    private final zombieSurvival plugin;
    public final HashMap<UUID, Long> cooldowns = new HashMap<>(); // 쿨타임 상태 저장

    public zombieItemCommand(zombieSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("zombieshop")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (plugin.getZombieTeam().contains(player.getUniqueId()) && plugin.gameStart && plugin.readyShop) {
                    showZombieItems(player);
                } else {
                    player.sendMessage(ChatColor.RED + "사용할 수 없습니다!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "이 명령어는 플레이어만 사용할 수 있습니다.");
            }
            return true;
        }
        return false;
    }

    private void showZombieItems(Player player) {
        Inventory inventory = getServer().createInventory(null, 9, "좀비 상점");

        // 아이템 정의
        ItemStack item1 = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta1 = item1.getItemMeta();
        if (meta1 != null) {
            meta1.setDisplayName(ChatColor.GOLD + "발광 효과");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GREEN + "생존자들에게 30초간 발광 효과를 부여합니다.");
            lore.add(" ");
            lore.add(ChatColor.GRAY + "구매: 석탄 9개");
            lore.add(ChatColor.RED + "재사용 쿨타임: 60초");
            meta1.setLore(lore);
            item1.setItemMeta(meta1);
        }

        ItemStack item2 = new ItemStack(Material.CREEPER_HEAD);
        ItemMeta meta2 = item2.getItemMeta();
        if (meta2 != null) {
            meta2.setDisplayName(ChatColor.GREEN + "크리퍼 머리");
            meta2.setLore(Collections.singletonList(ChatColor.WHITE + "궁금하면 클릭해보세요."));
            item2.setItemMeta(meta2);
        }

        // 아이템을 인벤토리에 추가
        inventory.addItem(item1);
        inventory.addItem(item2);

        // 플레이어에게 인벤토리 열기
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 상점 인벤토리 클릭 확인
        if (event.getView().getTitle().equals("좀비 상점")) {
            // 클릭된 아이템을 가져옴
            ItemStack clickedItem = event.getCurrentItem();

            // 아이템이 null이 아니고, 실제 아이템일 경우
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                Player player = (Player) event.getWhoClicked();

                // 클릭 시 이벤트를 취소
                event.setCancelled(true);

                // 플레이어에게 피드백
                applyItemEffect(clickedItem, player);
            }
        }
    }

    private void applyItemEffect(ItemStack item, Player player) {
        // 플레이어 인벤토리에 가 있는지 확인
        int coalCount = 0;
        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem != null && invItem.getType() == Material.COAL) {
                coalCount += invItem.getAmount();
            }
        }

        long currentTime = System.currentTimeMillis();
        long cooldownDuration = TimeUnit.SECONDS.toMillis(60); // 쿨타임 60초
        Long lastUseTime = cooldowns.get(player.getUniqueId());

        if (item.getType() == Material.SPECTRAL_ARROW) {
            if (lastUseTime != null) {
                long elapsed = currentTime - lastUseTime;
                long remaining = cooldownDuration - elapsed;

                if (remaining > 0) {
                    // 남은 쿨타임을 초 단위로 변환
                    long remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(remaining);
                    player.playSound(player.getLocation(), Sound.ENTITY_SKELETON_DEATH, 1.0f, 1.0f);
                    player.sendMessage(ChatColor.RED + "발광 효과는 " + remainingSeconds + "초 후에 다시 사용할 수 있습니다.");
                    player.closeInventory();
                    return;
                }
            }

            if (coalCount >= 9) {
                // 생존자 리스트 가져오기
                List<UUID> survivorUUIDs = plugin.getPlayerTeam();

                // 모든 생존자에게 발광 효과 적용
                for (UUID uuid : survivorUUIDs) {
                    Player survivor = Bukkit.getPlayer(uuid);
                    if (survivor != null && survivor.isOnline()) {
                        survivor.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 30, 1)); // 발광 효과 30초
                        survivor.playSound(survivor.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.0f, 1.0f);
                        survivor.sendMessage(ChatColor.RED + "좀비가 생존자 발광 효과를 사용했습니다!");
                    }
                }

                // 좀비 팀에도 알림 보내기
                List<UUID> zombieUUIDs = plugin.getZombieTeam();
                for (UUID uuid : zombieUUIDs) {
                    if (!uuid.equals(player.getUniqueId())) { // 발광 효과를 사용한 플레이어의 UUID와 비교(발광 효과를 사용한 플레이어는 이 알림이 안뜨게)
                        Player zombie = Bukkit.getPlayer(uuid);
                        if (zombie != null && zombie.isOnline()) {
                            zombie.sendMessage(ChatColor.GREEN + "다른 좀비가 상점에서 발광 효과를 사용했습니다!"); // 메시지 전송
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                        }
                    }
                }

                // 석탄 9개 제거
                player.getInventory().removeItem(new ItemStack(Material.COAL, 9));
                player.closeInventory();

                // 석탄 9개가 있는 경우 클릭 시 효과
                player.sendMessage(ChatColor.GREEN + "생존자에게 발광 효과가 30초간 적용됩니다!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);

                // 쿨타임 기록
                cooldowns.put(player.getUniqueId(), currentTime);
            } else {
                // 자원이 충분하지 않을 때 메시지 표시
                player.playSound(player.getLocation(), Sound.ENTITY_SKELETON_DEATH, 1.0f, 1.0f);
                player.sendMessage(ChatColor.RED + "자원이 부족합니다! (석탄 9개 필요)");
                player.closeInventory();
            }
        } else if (item.getType() == Material.CREEPER_HEAD) {
            // 크리퍼 머리 클릭
            player.sendMessage(ChatColor.GREEN + "이게 뭔지 궁금하셨겠지만 사실 아무 효과 없습니다.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
            player.closeInventory();
        }
    }
}
