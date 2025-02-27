package org.gacstudio.zomSur;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class playerTeamChat implements CommandExecutor, Listener {
    private final zombieSurvival plugin;
    private final Map<UUID, Boolean> globalChatStatus = new HashMap<>(); // true = 전체 채팅, false = 팀 채팅

    public playerTeamChat(zombieSurvival plugin) {
        this.plugin = plugin;
    }

    public void setGlobalChat(UUID playerUUID, boolean isGlobal) {
        globalChatStatus.put(playerUUID, isGlobal);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.gameStart) {
            return; // 게임이 시작되지 않았다면 전체 채팅 유지
        }

        Player sender = event.getPlayer();
        UUID senderUUID = sender.getUniqueId();
        boolean isGlobalChat = globalChatStatus.getOrDefault(senderUUID, false); // 기본값은 팀 채팅

        if (isGlobalChat) {
            // 전체 채팅 모드에서는 모든 플레이어가 메시지를 받을 수 있도록 유지
            event.getRecipients().clear();
            event.getRecipients().addAll(plugin.getServer().getOnlinePlayers());
            event.setFormat(ChatColor.YELLOW + "[전체] " + ChatColor.WHITE + "<%s> %s");
            return;
        }

        // 팀 채팅 모드
        event.getRecipients().clear(); // 모든 수신자 제거 후 특정 팀만 추가

        // 생존자 팀
        if (plugin.getPlayerTeam().contains(senderUUID)) {
            event.getRecipients().addAll(plugin.getPlayerTeam().stream()
                    .map(uuid -> plugin.getServer().getPlayer(uuid))
                    .filter(player -> player != null)
                    .toList());
            event.setFormat(ChatColor.GREEN + "[생존자] " + ChatColor.WHITE + "<%s> %s");
        }
        // 좀비 팀
        else if (plugin.getZombieTeam().contains(senderUUID)) {
            event.getRecipients().addAll(plugin.getZombieTeam().stream()
                    .map(uuid -> plugin.getServer().getPlayer(uuid))
                    .filter(player -> player != null)
                    .toList());
            event.setFormat(ChatColor.RED + "[좀비] " + ChatColor.WHITE + "<%s> %s");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("콘솔에서는 사용할 수 없는 명령입니다.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        if (!plugin.gameStart) {
            player.sendMessage(ChatColor.RED + "이 명령어는 게임 시작 후 사용할 수 있습니다.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("teamchat")) {
            if(plugin.getPlayerTeam().contains(playerUUID) || plugin.getZombieTeam().contains(playerUUID))
            {
                setGlobalChat(playerUUID, false);
                player.sendMessage(ChatColor.GREEN + "팀 채팅 모드입니다.");
            }
            else {
                player.sendMessage(ChatColor.RED + "이 명령어를 사용할 수 없습니다.");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("allchat")) {
            setGlobalChat(playerUUID, true);
            player.sendMessage(ChatColor.YELLOW + "전체 채팅 모드입니다.");
            return true;
        }

        return false;
    }
}
