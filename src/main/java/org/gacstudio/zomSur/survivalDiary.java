package org.gacstudio.zomSur;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class survivalDiary extends JavaPlugin implements CommandExecutor, Listener {

    private zombieItemCommand zombieItemCommand;
    private dataLoad dataLoad;

    private final List<UUID> playerTeam = new ArrayList<>();
    private final List<UUID> zombieTeam = new ArrayList<>();
    private Location centralChestLocation; // 중앙 상자의 위치

    // 좀비 킬 감지
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();

    private Location[] survivorSpawnPoints;
    private Location[] zombieSpawnPoints;

    public String worldName;

    public boolean isReady = false;
    public boolean gameStart = false;
    public boolean readyShop = false;
    public boolean randomgetrunning = false;

    int minX = -200;
    int maxX = 200;
    int minZ = -200;
    int maxZ = 200;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        dataLoad = new dataLoad(this);
        zombieItemCommand = new zombieItemCommand(this);

        dataLoad.getWorldNameFromConfig();
        dataLoad.loadConfig();

        Objects.requireNonNull(getCommand("zomsur")).setExecutor(this);
        getServer().getPluginManager().registerEvents(zombieItemCommand, this);
        Objects.requireNonNull(getCommand("zombieshop")).setExecutor(new zombieItemCommand(this));

        // 플레이어 팀 채팅 기능 등록 (이벤트 + 명령어 실행자)
        playerTeamChat teamChatHandler = new playerTeamChat(this);
        getServer().getPluginManager().registerEvents(teamChatHandler, this);
        Objects.requireNonNull(getCommand("teamchat")).setExecutor(teamChatHandler);
        Objects.requireNonNull(getCommand("allchat")).setExecutor(teamChatHandler);

        // TabCompleter 등록
        Objects.requireNonNull(getCommand("zomsur")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("zombieshop")).setTabCompleter(this);
    }
    public List<UUID> getZombieTeam() {
        return zombieTeam;
    }
    public List<UUID> getPlayerTeam() {
        return playerTeam;
    }
    public Map<String, String> loadLanguageMap() {
        try (InputStream in = getResource("lang/ko_kr.json");
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    private Location[] generateRandomSpawnPoints(World world, int count) {
        Location[] spawnPoints = new Location[count];
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            Location spawnPoint = null;
            do {
                int x = random.nextInt(maxX - minX + 1) + minX;
                int z = random.nextInt(maxZ - minZ + 1) + minZ;
                int y = world.getHighestBlockYAt(x, z) + 1; // 가장 높은 블록 위

                // x, z가 -50 ~ 50 범위 내에 있는지 확인
                if ((x > -50 && x < 50) && (z > -50 && z < 50)) {
                    continue; // 중앙 영역 내에 있으면 다른 좌표 생성
                }

                spawnPoint = new Location(world, x, y, z);

            } while (spawnPoint == null); // while 루프의 조건이 항상 참이므로 사실상 불필요함

            spawnPoints[i] = spawnPoint;
        }
        return spawnPoints;
    }

    @Override
    public void onDisable()
    {
        endGame();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("zomsur")) {
            // 첫 번째 인수에 대한 자동 완성
            if (args.length == 1) {
                List<String> subCommands = Arrays.asList("start", "setwinitem", "setwinitemrandom", "stop", "setteam", "leave");
                String input = args[0].toLowerCase(); // 사용자가 입력한 값

                // 입력값과 일치하는 명령어만 필터링하여 반환
                return subCommands.stream()
                        .filter(cmd -> cmd.startsWith(input))
                        .collect(Collectors.toList());
            }

            // 두 번째 인수에 대한 자동 완성 (setWinItem 명령어일 경우)
            if (args.length == 2 && args[0].equalsIgnoreCase("setwinitem")) {
                List<String> materialNames = new ArrayList<>();
                String input = args[1].toUpperCase(); // 사용자가 입력한 값

                for (Material material : Material.values()) {
                    if (material.name().startsWith(input)) { // 입력값과 일치하는 것만 추가
                        materialNames.add(material.name());
                    }
                }

                return materialNames; // 필터링된 목록 반환
            }

            // 두 번째 인수에 대한 자동 완성 (setTeam 명령어일 경우)
            if (args.length == 2 && args[0].equalsIgnoreCase("setteam")) {
                List<String> teams = Arrays.asList("survivor", "zombie");
                String input = args[1].toLowerCase(); // 사용자가 입력한 값

                // 입력값과 일치하는 팀 이름만 필터링하여 반환
                return teams.stream()
                        .filter(team -> team.startsWith(input))
                        .collect(Collectors.toList());
            }
            // 세 번째 인수에 대한 자동 완성 (setTeam 명령어일 경우)
            if (args.length == 3 && args[0].equalsIgnoreCase("setteam")) {
                List<String> playerNames = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playerNames.add(player.getName());
                }
                return playerNames;
            }

            // 두 번째 인수에 대한 자동 완성 (leave 명령어일 경우)
            if (args.length == 2 && args[0].equalsIgnoreCase("leave")) {
                List<String> playerNames = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playerNames.add(player.getName());
                }
                return playerNames;
            }
        }

        return new ArrayList<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final JavaPlugin plugin = this;
        if (command.getName().equalsIgnoreCase("zomsur")) {
            if (args.length == 0 || (!args[0].equalsIgnoreCase("start") && !args[0].equalsIgnoreCase("stop") && !args[0].equalsIgnoreCase("setteam") && !args[0].equalsIgnoreCase("leave") && !args[0].equalsIgnoreCase("setwinitem")) && !args[0].equalsIgnoreCase("setwinitemrandom")) {
                sender.sendMessage(ChatColor.RED + "사용법: /zomsur start 또는 /zomsur stop 또는 /zomsur setteam 또는 /zomsur leave 또는 /zomsur setwinitem 또는 /zomsur setwinitemrandom");
                return true;
            }

            // 명령어를 사용하는 플레이어의 위치를 확인합니다.
            if (sender instanceof Player) {
                Player player = (Player) sender;
                // 플레이어가 원하는 월드에 있는지 확인합니다.
                if (!(player.getWorld().getName().equalsIgnoreCase(worldName)
                        || player.getWorld().getName().equalsIgnoreCase(worldName + "_nether")
                        || player.getWorld().getName().equalsIgnoreCase(worldName + "_the_end"))) {
                    player.sendMessage(ChatColor.RED + "이 명령어는 " + worldName + " 세계와 해당 세계의 네더와 엔드에서만 사용할 수 있습니다.");
                    return true;
                }
            }

            if (args[0].equalsIgnoreCase("start")) {
                if (gameStart) {
                    sender.sendMessage(ChatColor.RED + "게임이 이미 진행 중입니다! /zomsur stop 으로 종료 후 실행하세요.");
                    return true;
                }

                World world = Bukkit.getWorld(worldName);
                List<Player> onlinePlayers = Bukkit.getOnlinePlayers().stream().filter(player -> player.getWorld().equals(world)).collect(Collectors.toList());
                if (onlinePlayers.size() < 2) {
                    sender.sendMessage(ChatColor.RED + "플레이어가 충분하지 않습니다. 최소 2명이 필요합니다.");
                    return true;
                }

                Bukkit.getWorld(dataLoad.getWorldNameFromConfig());
                // World 초기화 및 기타 설정
                getServer().getScheduler().runTask(this, () -> {
                    survivorSpawnPoints = generateRandomSpawnPoints(world, 10);

                    zombieSpawnPoints = new Location[]{
                            new Location(world, 5, world.getHighestBlockYAt(5, 5) + 2, 5),
                            new Location(world, 10, world.getHighestBlockYAt(10, 10) + 2, 10),
                            new Location(world, -10, world.getHighestBlockYAt(-10, -10) + 2, -10)
                    };
                });

                zombieTeam.clear();
                playerTeam.clear();
                world.setGameRuleValue("doImmediateRespawn", "true");
                world.setGameRuleValue("locatorBar", "false");
                gameStart = true;
                isReady = true;

                // 랜덤하게 좀비로 선정(기본값 40%)
                int zombiePercent = getConfig().getInt("zombie-percent", 40);
                int zombieCount = Math.max(1, Math.round(onlinePlayers.size() * zombiePercent / 100.0f));

                // 플레이어 섞기
                Collections.shuffle(onlinePlayers);

                // 좀비 선정
                for (int i = 0; i < onlinePlayers.size(); i++) {
                    Player player = onlinePlayers.get(i);
                    if (i < zombieCount) {
                        zombieTeam.add(player.getUniqueId());
                    } else {
                        playerTeam.add(player.getUniqueId());
                    }
                }

                // 모든 플레이어에게 게임이 곧 시작된다는 메시지 전송
                sendStartMessageToAll();
                Bukkit.getLogger().info("The game is ready to start.");

                // 5초 카운트다운 후에 팀 정보 알림
                Bukkit.getScheduler().runTaskLater(this, this::startCountdown, 5 * 20L);
                Bukkit.getScheduler().runTaskLater(this, this::sendTeamInfoToPlayers, 10 * 20L);

                return true;
            }
            else if (args[0].equalsIgnoreCase("stop")) {
                if (!gameStart) {
                    sender.sendMessage(ChatColor.RED + "The game has stopped.");
                    return true;
                }

                // 게임 종료 처리
                endGame();
                sender.sendMessage(ChatColor.GREEN + "게임이 중지되었습니다.");
                Bukkit.getLogger().info("The game has stopped.");
                return true;
            }
            else if (args[0].equalsIgnoreCase("setteam")) {
                if (!gameStart || isReady) {
                    sender.sendMessage(ChatColor.RED + "게임이 시작되지 않았거나 준비 중이라 설정할 수 없습니다.");
                    return true;
                }

                if (args.length != 3) {
                    sender.sendMessage(ChatColor.RED + "사용법: /zomsur setteam <survivor | zombie> <Player>");
                    return true;
                }

                String teamType = args[1];
                Player targetPlayer = Bukkit.getPlayer(args[2]);

                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "플레이어 " + args[2] + " 을(를) 찾을 수 없습니다.");
                    return true;
                }

                UUID targetUUID = targetPlayer.getUniqueId();


                // 플레이어가 이미 한 팀에 속해 있는지 확인
                if (zombieTeam.contains(targetUUID)) {
                    sender.sendMessage(ChatColor.RED + targetPlayer.getName() + " 은(는) 이미 좀비 팀에 속해 있습니다.");
                    return true;
                }
                else if (playerTeam.contains(targetUUID)) {
                    sender.sendMessage(ChatColor.RED + targetPlayer.getName() + " 은(는) 이미 생존자 팀에 속해 있습니다.");
                    return true;
                }

                Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
                Team zombieTeamSCB = board.getTeam("zombies"); // zombies팀 불러오기

                // 선택한 팀에 맞게 플레이어를 할당
                if (teamType.equalsIgnoreCase("zombie")) {
                    zombieTeam.add(targetUUID);
                    zombieTeamSCB.addEntry(targetPlayer.getName());
                    targetPlayer.sendMessage(ChatColor.GREEN + "당신은 이제 좀비 팀입니다.");
                    sender.sendMessage(ChatColor.GREEN + targetPlayer.getName() + " 을(를) 좀비 팀에 추가했습니다.");
                    Bukkit.getLogger().info(ChatColor.GREEN + targetPlayer.getName() + " was added to the ZOMBIE team.");
                    updatePlayerListNames();
                    checkVictoryConditions();
                } else if (teamType.equalsIgnoreCase("survivor")) {
                    playerTeam.add(targetUUID);
                    targetPlayer.sendMessage(ChatColor.GREEN + "당신은 이제 생존자 팀입니다.");
                    sender.sendMessage(ChatColor.GREEN + targetPlayer.getName() + " 을(를) 생존자 팀에 추가했습니다.");
                    Bukkit.getLogger().info(ChatColor.GREEN + targetPlayer.getName() + " was added to the SURVIVOR team.");
                    updatePlayerListNames();
                    checkVictoryConditions();
                } else {
                    sender.sendMessage(ChatColor.RED + "잘못된 팀입니다. survivor 또는 zombie 중 하나를 선택하세요.");
                }

                return true;
            }
            else if (args[0].equalsIgnoreCase("leave")) {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "사용법: /zomsur leave <Player>");
                    return true;
                }

                if (!gameStart || isReady) {
                    sender.sendMessage(ChatColor.RED + "게임이 시작되지 않았거나 준비 중이라 플레이어를 제거할 수 없습니다.");
                    return true;
                }

                Player targetPlayer = Bukkit.getPlayer(args[1]);

                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "플레이어 " + args[1] + " 을(를) 찾을 수 없습니다.");
                    return true;
                }

                UUID targetUUID = targetPlayer.getUniqueId();

                Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
                Team zombieTeamSCB = board.getTeam("zombies"); // zombies팀 불러오기

                if (zombieTeam.contains(targetUUID)) {
                    zombieTeam.remove(targetUUID);
                    zombieTeamSCB.removeEntity(targetPlayer);
                    targetPlayer.sendMessage(ChatColor.GREEN + "게임에서 나가게 되었습니다. 좀비 팀에서 제거되었습니다.");

                    targetPlayer.setPlayerListName(targetPlayer.getName());

                    targetPlayer.closeInventory();
                    targetPlayer.getInventory().clear();
                    targetPlayer.setMaxHealth(20);
                    targetPlayer.setHealth(targetPlayer.getAttribute(Attribute.MAX_HEALTH).getValue());
                    targetPlayer.setFoodLevel(20);
                    targetPlayer.setSaturation(20.0f); // 포화상태
                    targetPlayer.setExp(0);
                    targetPlayer.setLevel(0);
                    for (PotionEffect effect : targetPlayer.getActivePotionEffects()) {
                        targetPlayer.removePotionEffect(effect.getType());
                    }
                    targetPlayer.setGameMode(GameMode.ADVENTURE);
                    zombieItemCommand.cooldowns.remove(targetPlayer.getUniqueId());
                } else if (playerTeam.contains(targetUUID)) {
                    playerTeam.remove(targetUUID);
                    targetPlayer.sendMessage(ChatColor.GREEN + "게임에서 나가게 되었습니다. 생존자 팀에서 제거되었습니다.");

                    targetPlayer.setPlayerListName(targetPlayer.getName());

                    targetPlayer.closeInventory();
                    targetPlayer.getInventory().clear();
                    targetPlayer.setMaxHealth(20);
                    targetPlayer.setHealth(targetPlayer.getAttribute(Attribute.MAX_HEALTH).getValue());
                    targetPlayer.setFoodLevel(20);
                    targetPlayer.setSaturation(20.0f); // 포화상태
                    targetPlayer.setExp(0);
                    targetPlayer.setLevel(0);
                    for (PotionEffect effect : targetPlayer.getActivePotionEffects()) {
                        targetPlayer.removePotionEffect(effect.getType());
                    }
                    targetPlayer.setGameMode(GameMode.ADVENTURE);
                } else {
                    sender.sendMessage(ChatColor.RED + "플레이어 " + targetPlayer.getName() + " 은(는) 게임에 참가하지 않고 있습니다.");
                    return true;
                }

                // 나간 플레이어에게 알림 및 탭 목록 업데이트
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(ChatColor.RED + targetPlayer.getName() + "님이 게임에서 나갔습니다.");
                }

                updatePlayerListNames();
                checkVictoryConditions();

                return true;
            }
            else if (args[0].equalsIgnoreCase("setwinitem"))
            {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "사용법: /zomsur setwinitem <ITEM>");
                    return true;
                }
                if(gameStart)
                {
                    sender.sendMessage(ChatColor.RED + "게임 중에는 승리 조건 아이템을 변경할 수 없습니다.");
                    return true;
                }
                try {
                    Material newItem = Material.valueOf(args[1].toUpperCase());
                    dataLoad.winItem = newItem;

                    sender.sendMessage(ChatColor.GREEN + "승리 조건 아이템이 " + ChatColor.GOLD + newItem.name() + ChatColor.GREEN + "으로 설정되었습니다!");
                    getLogger().info("Set Victory Item : " + newItem.name());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "잘못된 아이템 이름입니다. " + e);
                }
                return true;
            }
            else if (args[0].equalsIgnoreCase("setwinitemrandom")) {
                if (gameStart) {
                    sender.sendMessage(ChatColor.RED + "게임 중에는 변경할 수 없습니다.");
                    return true;
                }
                if(randomgetrunning)
                {
                    sender.sendMessage(ChatColor.RED + "이미 아이템을 추첨 중입니다.");
                    return true;
                }

                randomgetrunning = true;

                Set<Material> bannedItems = Set.of(
                        //AdminBlock
                        Material.COMMAND_BLOCK,
                        Material.CHAIN_COMMAND_BLOCK,
                        Material.REPEATING_COMMAND_BLOCK,
                        Material.COMMAND_BLOCK_MINECART,
                        Material.JIGSAW,
                        Material.STRUCTURE_BLOCK,
                        Material.STRUCTURE_VOID,
                        Material.BARRIER,
                        Material.DEBUG_STICK,
                        Material.TEST_INSTANCE_BLOCK,
                        Material.TEST_BLOCK,
                        Material.LIGHT,
                        Material.KNOWLEDGE_BOOK,

                        // SurvivalBlock
                        Material.AIR,
                        Material.CAVE_AIR,
                        Material.VOID_AIR,
                        Material.WATER,
                        Material.LAVA,
                        Material.BEDROCK,
                        Material.SPAWNER,
                        Material.BUBBLE_COLUMN,
                        Material.MOVING_PISTON,
                        Material.FIRE,
                        Material.SOUL_FIRE,
                        Material.POWDER_SNOW,
                        Material.END_GATEWAY,
                        Material.END_PORTAL,
                        Material.END_PORTAL_FRAME,
                        Material.NETHER_PORTAL,
                        Material.PLAYER_HEAD,
                        Material.PLAYER_WALL_HEAD,
                        Material.ZOMBIE_WALL_HEAD,
                        Material.CREEPER_WALL_HEAD,
                        Material.DRAGON_WALL_HEAD,
                        Material.SKELETON_WALL_SKULL,
                        Material.FROGSPAWN,
                        Material.PETRIFIED_OAK_SLAB,
                        Material.REINFORCED_DEEPSLATE
                );

                List<Material> itemList = Arrays.stream(Material.values())
                        .filter(Material::isItem)
                        .filter(m -> !bannedItems.contains(m))
                        .filter(m -> !m.name().endsWith("_SPAWN_EGG"))
                        .collect(Collectors.toList());

                Map<String, String> langMap = loadLanguageMap(); // 여기서 목록 로드
                new BukkitRunnable() {
                    int ticks = 0;
                    int totalDuration = 200;
                    int delayCounter = 0;
                    int currentDelay = 2;
                    Material chosenItem = null;

                    int soundCycle = 0;
                    Sound[] loopSounds = {
                            Sound.BLOCK_NOTE_BLOCK_PLING,
                            Sound.BLOCK_NOTE_BLOCK_PLING,
                            Sound.BLOCK_NOTE_BLOCK_PLING,
                            Sound.BLOCK_NOTE_BLOCK_PLING
                    };
                    float[] pitches = {
                            1.2f,
                            1.0f,
                            1.2f,
                            1.6f
                    };

                    public void run() {
                        if (ticks >= totalDuration) {
                            dataLoad.winItem = chosenItem;
                            String key = "item.minecraft." + chosenItem.name().toLowerCase();
                            if (!langMap.containsKey(key)) {
                                key = "block.minecraft." + chosenItem.name().toLowerCase();
                            }
                            String displayName = langMap.getOrDefault(key, chosenItem.name());

                            for (Player p : Bukkit.getOnlinePlayers()) {
                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1.0f);

                                TextComponent msg = new TextComponent(ChatColor.GREEN + "승리 아이템이 ");
                                TextComponent itemName = new TextComponent(ChatColor.GOLD + displayName);
                                TextComponent msg2 = new TextComponent(ChatColor.GREEN + "(으)로 결정되었습니다!");
                                itemName.setHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        new ComponentBuilder(ChatColor.GRAY + "Material: " + chosenItem.name()).create()
                                ));
                                msg.addExtra(itemName);
                                msg.addExtra(msg2);
                                p.spigot().sendMessage(msg);
                                //p.sendMessage(ChatColor.GREEN + "승리 아이템이 " + ChatColor.GOLD + displayName + ChatColor.GREEN + "(으)로 결정되었습니다!");
                            }

                            getLogger().info("Set Victory Item: " + chosenItem.name());
                            randomgetrunning = false;
                            cancel();
                            return;
                        }

                        if (delayCounter <= 0) {
                            chosenItem = itemList.get(random.nextInt(itemList.size()));

                            String key = "item.minecraft." + chosenItem.name().toLowerCase();
                            if (!langMap.containsKey(key)) {
                                key = "block.minecraft." + chosenItem.name().toLowerCase();
                            }
                            String displayName = langMap.getOrDefault(key, chosenItem.name());


                            Sound currentSound = loopSounds[soundCycle % 4];
                            float currentPitch = pitches[soundCycle % 4];
                            soundCycle++;
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                p.sendTitle(
                                        ChatColor.GOLD + displayName,
                                        ChatColor.YELLOW + "승리 아이템 정하는 중..",
                                        0, 20, 10
                                );
                                p.playSound(p.getLocation(), currentSound, 1.0f, currentPitch);
                            }

                            currentDelay = (int) (2 + (ticks / 10.0));
                            delayCounter = currentDelay;
                            ticks += currentDelay;
                        } else {
                            delayCounter--;
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);

                return true;
            }
            return false;
        }
        return false;
    }

    private void startCountdown() {
        for (int i = 5; i > 0; i--) {
            final int count = i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                // 생존자 팀 카운트다운
                for (UUID uuid : playerTeam) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        sendBigTitle(player, count > 3 ? ChatColor.YELLOW + String.valueOf(count) : ChatColor.RED + String.valueOf(count), "");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }

                // 좀비 팀 카운트다운
                for (UUID uuid : zombieTeam) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        sendBigTitle(player, count > 3 ? ChatColor.YELLOW + String.valueOf(count) : ChatColor.RED + String.valueOf(count), "");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }
            }, (5 - i) * 20L); // 1초마다 실행
        }
    }

    private void sendStartMessageToAll() {
        // 모든 플레이어에게 "게임이 곧 시작됩니다!" 메시지 전송
        for (UUID uuid : getAllPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 1.0f, 1.0f);
                sendBigTitle(player, ChatColor.YELLOW + "게임이 곧 시작됩니다!", "");
            }
        }
    }
    private List<UUID> getAllPlayers() {
        List<UUID> allPlayers = new ArrayList<>();
        allPlayers.addAll(playerTeam);
        allPlayers.addAll(zombieTeam);
        return allPlayers;
    }

    private void sendTeamInfoToPlayers() {
        World world = Bukkit.getWorld(worldName);
        // 중앙 상자 생성
        centralChestLocation = new Location(world, 0, world.getHighestBlockYAt(0, 0) + 5, 0);
        createCentralChest();

        isReady = false;
        readyShop = true;

        Map<String, String> langMap = loadLanguageMap(); // 여기서 목록 로드
        String key = "item.minecraft." + dataLoad.winItem.name().toLowerCase();
        if (!langMap.containsKey(key)) {
            key = "block.minecraft." + dataLoad.winItem.name().toLowerCase();
        }
        String displayName = langMap.getOrDefault(key, dataLoad.winItem.name());

        // 생존자 팀 설정
        for (int i = 0; i < playerTeam.size(); i++) {
            Player player = Bukkit.getPlayer(playerTeam.get(i));
            if (player != null && player.isOnline()) {
                // 스폰 포인트 배열의 길이를 초과하면 인덱스를 순환
                Location spawnLocation = survivorSpawnPoints[i % survivorSpawnPoints.length];
                player.teleport(spawnLocation);
                player.setBedSpawnLocation(spawnLocation, true);

                // 플레이어팀 기본설정
                player.closeInventory();
                player.getInventory().clear();
                player.setMaxHealth(20);
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                player.setFoodLevel(20);
                player.setSaturation(20.0f); // 포화상태
                player.setExp(0);
                player.setLevel(0);
                player.setGameMode(GameMode.SURVIVAL);

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                sendBigTitle(player, ChatColor.GREEN + "생존자", ChatColor.WHITE + "좀비로부터 도망치고, 살아남으세요!");

                // 전송
                TextComponent base = new TextComponent(ChatColor.GREEN + "\n\n당신은 생존자 입니다!");
                base.addExtra(new TextComponent(ChatColor.WHITE + "\n\n최대한 빠르게 아이템을 얻어 중앙상자(0, ~, 0)에 넣으세요!"));
                base.addExtra(new TextComponent(ChatColor.RED + "\n\n서두르세요. 좀비는 중앙 상자를 방어하고 있거나 당신을 감염시키기 위해 추격하고 있을 것입니다!\n\n"));
                base.addExtra(new TextComponent(ChatColor.WHITE + "채팅 명령어:\n/teamchat : 팀 채팅으로 전환\n/allchat : 전체 채팅으로 전환\n\n"));
                base.addExtra(new TextComponent(ChatColor.GOLD + "생존자 승리 조건 아이템: "));
                // 아이템 이름에 HoverEvent 붙이기
                TextComponent hoverItem = new TextComponent(ChatColor.WHITE + displayName);
                hoverItem.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        //new ComponentBuilder("Material: " + dataLoad.winItem.name()).color(ChatColor.GRAY.asBungee()).create()
                        new ComponentBuilder(ChatColor.GRAY + "Material: " + dataLoad.winItem.name()).create()
                ));
                base.addExtra(hoverItem);

                // 보내기!
                player.spigot().sendMessage(base);
                //player.sendMessage(ChatColor.GREEN + "\n\n당신은 생존자 입니다!" + ChatColor.WHITE + "\n\n최대한 빠르게 아이템을 얻어 중앙상자(0, ~, 0)에 넣으세요!" + ChatColor.RED + "\n\n서두르세요. 좀비는 중앙 상자를 방어하고 있거나 당신을 감염시키기 위해 추격하고 있을 것입니다!\n\n" + ChatColor.WHITE + "채팅 명령어:\n" + "/teamchat : 팀 채팅으로 전환\n/allchat : 전체 채팅으로 전환\n\n" + ChatColor.GOLD + "생존자 승리 조건 아이템: " + ChatColor.WHITE + displayName);
            }
        }

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team zombieTeamSCB = board.getTeam("zombies"); // zombies팀 불러오기

        // 좀비 팀 스코어보드 생성
        if (zombieTeamSCB == null) {
            zombieTeamSCB = board.registerNewTeam("zombies"); // 없으면 새로 생성
        }
        zombieTeamSCB.setColor(ChatColor.RED); // 닉네임을 빨간색으로 설정

        // 좀비팀 기본설정
        for (int i = 0; i < zombieTeam.size(); i++) {
            Player player = Bukkit.getPlayer(zombieTeam.get(i));
            if (player != null && player.isOnline()) {
                // 스폰 포인트 배열의 길이를 초과하면 인덱스를 순환
                Location spawnLocation = zombieSpawnPoints[i % zombieSpawnPoints.length];
                player.teleport(spawnLocation);
                player.setBedSpawnLocation(spawnLocation, true);

                // 좀비팀 기본설정
                player.closeInventory();
                player.getInventory().clear();
                player.setMaxHealth(40); // 좀비는 더 많은 체력
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                player.setFoodLevel(20);
                player.setSaturation(20.0f); // 포화상태
                player.setExp(0);
                player.setLevel(0);
                player.setGameMode(GameMode.SURVIVAL);

                // 스코어보드에 좀비팀 플레이어 추가
                zombieTeamSCB.addEntry(player.getName());

                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
                sendBigTitle(player, ChatColor.RED + "좀비", ChatColor.WHITE + "생존자를 추격하고 감염시키세요!");

                // 기본 메시지 구성
                TextComponent base = new TextComponent(ChatColor.RED + "\n\n당신은 좀비 입니다!");
                base.addExtra(new TextComponent(ChatColor.WHITE + "\n\n생존자들을 처리해 모두 좀비로 만드세요!"));
                base.addExtra(new TextComponent(ChatColor.GOLD + "\n/zombieshop 명령어로 생존자를 처리하는데 도움을 주는 물건을 구입할 수 있습니다!"));
                base.addExtra(new TextComponent(ChatColor.RED + "\n\n서두르세요. 생존자는 추가 좀비 감염을 막기 위해 중앙 상자(0, ~, 0)에 접근할 것입니다! 상자를 방어하는것도 잊지 마세요!\n\n"));
                base.addExtra(new TextComponent(ChatColor.WHITE + "채팅 명령어:\n/teamchat : 팀 채팅으로 전환\n/allchat : 전체 채팅으로 전환\n\n"));
                base.addExtra(new TextComponent(ChatColor.GOLD + "생존자 승리 조건 아이템: "));
                // HoverEvent가 달린 displayName 컴포넌트
                TextComponent hoverItem = new TextComponent(ChatColor.WHITE + displayName);
                hoverItem.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ChatColor.GRAY + "Material: " + dataLoad.winItem.name()).create()
                        //new ComponentBuilder("Material: " + dataLoad.winItem.name()).color(ChatColor.GRAY.asBungee()).create()
                ));
                base.addExtra(hoverItem);

                // 메시지 전송
                player.spigot().sendMessage(base);
                //player.sendMessage(ChatColor.RED + "\n\n당신은 좀비 입니다!" + ChatColor.WHITE + "\n\n생존자들을 처리해 모두 좀비로 만드세요!" + ChatColor.GOLD + "\n/zombieshop 명령어로 생존자를 처리하는데 도움을 주는 물건을 구입할 수 있습니다!" + ChatColor.RED + "\n\n서두르세요. 생존자는 추가 좀비 감염을 막기 위해 중앙 상자(0, ~, 0)에 접근할 것입니다! 상자를 방어하는것도 잊지 마세요!\n\n" + ChatColor.WHITE + "채팅 명령어:\n" + "/teamchat : 팀 채팅으로 전환\n/allchat : 전체 채팅으로 전환\n\n" + ChatColor.GOLD + "생존자 승리 조건 아이템: " + ChatColor.WHITE + displayName);
            }
        }

        // 팀에 따라 플레이어의 탭 목록 이름을 업데이트
        updatePlayerListNames();
    }

    private void sendBigTitle(Player player, String title, String subtitle) {
        player.sendTitle(title, subtitle, 10, 50, 20);
    }

    // 상자파괴방지
    // 그냥 부술 때
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if(gameStart)
        {
            Block block = event.getBlock();

            // 블록의 위치가 중앙 상자 위치와 같은지 확인
            if (block.getWorld().equals(Bukkit.getWorld(worldName)) && block.getLocation().equals(centralChestLocation)) {
                event.getPlayer().sendMessage(ChatColor.RED + "이 상자는 파괴할 수 없습니다!");
                event.setCancelled(true); // 블록 파괴 취소
            }
        }
    }
    // 폭발할 때
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if(gameStart)
        {
            List<Block> blocks = new ArrayList<>(event.blockList());
            for (Block block : blocks) {
                if (block.getWorld().equals(Bukkit.getWorld(worldName)) && block.getLocation().equals(centralChestLocation)) {
                    event.blockList().remove(block);
                }
            }
        }
    }
    // 엔티티에 의해 폭발할 때(크리퍼 등)
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if(gameStart)
        {
            List<Block> blocks = new ArrayList<>(event.blockList());
            for (Block block : blocks) {
                if (block.getWorld().equals(Bukkit.getWorld(worldName)) && block.getLocation().equals(centralChestLocation)) {
                    event.blockList().remove(block);
                }
            }
        }
    }
    // 불에 탈때
    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (gameStart) {
            Block block = event.getBlock();

            // 블록의 월드와 중앙 상자의 월드가 같은지 확인
            if (block.getWorld().equals(Bukkit.getWorld(worldName)) && block.getLocation().equals(centralChestLocation)) {
                event.setCancelled(true);
            }
        }
    }
    // 블록 물리 이벤트 취소 (형태 변형 방지)
    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (gameStart) {
            Block block = event.getBlock();

            // 중앙 상자의 위치와 같은 경우
            if (block.getWorld().equals(Bukkit.getWorld(worldName)) && block.getLocation().equals(centralChestLocation)) {
                if (block.getType() == Material.CHEST) {
                    event.setCancelled(true); // 블록 물리 이벤트 취소 (형태 변형 방지)
                }
            }
        }
    }
    // 다른 요인(예: 물이나 주변 블록 영향)으로 인해 형태가 바뀌는 것을 방지
    @EventHandler
    public void onBlockForm(BlockFormEvent event) {
        if (gameStart) {
            Block block = event.getBlock();

            if (block.getWorld().equals(Bukkit.getWorld(worldName)) && block.getLocation().equals(centralChestLocation)) {
                if (block.getType() == Material.CHEST) {
                    event.setCancelled(true); // 블록 변경 이벤트 취소
                }
            }
        }
    }

    // 상자 생성
    private void createCentralChest() {
        if (centralChestLocation != null) {
            Block block = centralChestLocation.getBlock();
            block.setType(Material.CHEST);

            Chest chest = (Chest) block.getState();
            Inventory inventory = chest.getInventory();

            // 중앙 상자를 비어 있게 초기화
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, null);
            }

            // 상자를 고정된 상태로 설정
            block.setType(Material.CHEST);
            block.setBlockData(Bukkit.createBlockData("minecraft:chest[facing=north,waterlogged=false]"));  // Set block data
            block.getState().update();
        } else {
            getLogger().severe("Central chest location is not set!");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team zombieTeamSCB = board.getTeam("zombies"); // zombies팀 불러오기

        UUID uuid = event.getPlayer().getUniqueId();
        lastDamager.remove(uuid);
        lastDamageTime.remove(uuid);

        Player quittingPlayer = event.getPlayer();
        if (isReady) {
            getLogger().info(quittingPlayer.getName() + " has left the server. Game progress has been cancelled.");

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(ChatColor.RED + quittingPlayer.getName() + " 님이 서버를 나갔습니다. 게임 진행이 취소되었습니다.");
            }

            endGame();
        }
        if (gameStart && !isReady) {
            UUID playerUUID = event.getPlayer().getUniqueId();

            // 생존자 팀에서 제거
            if (playerTeam.remove(playerUUID)) {
                getLogger().info(event.getPlayer().getName() + " has been removed from the SURVIVOR team.");
            }

            // 좀비 팀에서 제거
            if (zombieTeam.remove(playerUUID)) {
                zombieTeamSCB.removeEntry(quittingPlayer.getName());
                getLogger().info(event.getPlayer().getName() + " has been removed from the ZOMBIE team.");
            }

            // 승리상태확인
            checkVictoryConditions();
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deadPlayer = event.getEntity();
        UUID deadPlayerUUID = deadPlayer.getUniqueId();

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team zombieTeamSCB = board.getTeam("zombies");

        if (gameStart && !isReady) {
            UUID damagerUUID = null;

            EntityDamageEvent damageEvent = deadPlayer.getLastDamageCause();
            if (damageEvent instanceof EntityDamageByEntityEvent) {
                Entity damager = ((EntityDamageByEntityEvent) damageEvent).getDamager();
                if (damager instanceof Player) {
                    damagerUUID = ((Player) damager).getUniqueId();
                }
            }

            // 직접 데미지가 아니면, 최근에 맞은 플레이어 확인
            if (damagerUUID == null) {
                if (lastDamager.containsKey(deadPlayerUUID)) {
                    long lastHitTime = lastDamageTime.getOrDefault(deadPlayerUUID, 0L);
                    if (System.currentTimeMillis() - lastHitTime <= 10_000) { // 최근 10초 이내
                        damagerUUID = lastDamager.get(deadPlayerUUID);
                    }
                }
            }

            // 가해자가 유효한 좀비 플레이어인지 확인
            if (damagerUUID != null && zombieTeam.contains(damagerUUID)) {
                Player damagerPlayer = Bukkit.getPlayer(damagerUUID);
                if (playerTeam.remove(deadPlayerUUID)) {
                    zombieTeam.add(deadPlayerUUID);
                    zombieTeamSCB.addEntry(deadPlayer.getName());

                    if (playerTeam.size() > 1) {
                        sendBigTitle(deadPlayer, ChatColor.RED + "좀비가 되었습니다!", ChatColor.WHITE + "나머지 생존자를 모두 감염시키세요!");
                        deadPlayer.sendMessage(ChatColor.RED + "\n\n안타깝지만 당신은 좀비가 되었습니다!" + ChatColor.WHITE + "\n\n나머지 생존자들을 처리해 모두 좀비로 만드세요!" + ChatColor.GOLD + "\n/zombieshop 명령어로 생존자를 처리하는데 도움을 주는 물건을 구입할 수 있습니다!" + ChatColor.RED + "\n\n생존자는 추가 좀비 감염을 막기 위해 중앙 상자(0,0)에 접근할 것입니다! 상자를 방어하는것도 잊지 마세요!\n\n");
                    }

                    deadPlayer.setPlayerListName(ChatColor.RED + "[좀비] " + deadPlayer.getName());

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage(ChatColor.RED + deadPlayer.getName() + "님이 좀비 팀의 " + (damagerPlayer != null ? damagerPlayer.getName() : "알 수 없음") + "에게 사망하여 좀비 팀으로 이동했습니다!");
                    }

                    checkVictoryConditions();
                }
            }
        }
    }
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player damaged = (Player) event.getEntity();
            Player damager = (Player) event.getDamager();

            lastDamager.put(damaged.getUniqueId(), damager.getUniqueId());
            lastDamageTime.put(damaged.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (gameStart && !isReady) {
            if (event.getInventory() == null || !(event.getInventory().getHolder() instanceof Chest)) {
                return;
            }

            Chest chest = (Chest) event.getInventory().getHolder();

            // 특정 월드에서 중앙 상자만 감지
            World world = Bukkit.getWorld(worldName);
            if (chest.getWorld().equals(world) && isCentralChest(chest.getLocation())) {
                ItemStack item = event.getCurrentItem();
                Player itemInPlayer = (Player) event.getWhoClicked();

                // 아이템이 특정 아이템인지 확인
                if (item != null && item.getType() == dataLoad.winItem) {
                    // 생존자 팀의 승리 조건 처리
                    endGame();

                    // 해당 월드에 있는 플레이어들에게 메시지 전송
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        player.sendMessage(ChatColor.GOLD + "게임이 종료되었습니다!" + ChatColor.GREEN + " 생존자 팀이 승리했습니다!");
                        player.sendTitle(ChatColor.GOLD + "게임이 종료되었습니다!", ChatColor.GREEN + "생존자 팀이 승리했습니다", 0, 100, 20);
                        player.sendMessage(ChatColor.YELLOW + itemInPlayer.getName() + ChatColor.AQUA +"이(가) 상자에 아이템을 넣었습니다.");
                    }
                }
            }
        }
    }
    private boolean isCentralChest(Location location) {
        return location != null && location.equals(centralChestLocation);
    }

    private void checkVictoryConditions() {
        // 모든 플레이어가 좀비 팀인지 확인
        if (playerTeam.isEmpty()) {
            endGame();

            // 생존자 팀이 비어있다면, 좀비 팀의 승리
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 1.0f, 1.0f);
                player.sendMessage(ChatColor.GOLD + "게임이 종료되었습니다!" + ChatColor.RED + " 좀비 팀이 승리했습니다!");
                player.sendTitle(ChatColor.GOLD + "게임이 종료되었습니다!", ChatColor.RED + "좀비 팀이 승리했습니다", 0, 100, 20);
            }
        } else if (zombieTeam.isEmpty()) {
            endGame();

            // 좀비 팀이 비어있다면, 생존자 팀의 승리
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendMessage(ChatColor.GOLD + "게임이 종료되었습니다!" + ChatColor.GREEN + " 생존자 팀이 승리했습니다!");
                player.sendTitle(ChatColor.GOLD + "게임이 종료되었습니다!", ChatColor.GREEN + "생존자 팀이 승리했습니다", 0, 100, 20);
            }
        }
    }

    private void updatePlayerListNames() {
        // 생존자 팀의 플레이어 리스트 업데이트
        for (UUID uuid : playerTeam) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // 생존자는 [생존자] 접두사로 표시
                player.setPlayerListName(ChatColor.GREEN + "[생존자] " + player.getName());
            }
        }

        // 좀비 팀의 플레이어 리스트 업데이트
        for (UUID uuid : zombieTeam) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // 좀비는 [좀비] 접두사로 표시
                player.setPlayerListName(ChatColor.RED + "[좀비] " + player.getName());
            }
        }
    }

    Random random = new Random();

    int randomX = random.nextInt(201) - 100;
    int randomZ = random.nextInt(201) - 100;

    private void endGame() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team zombieTeamSCB = board.getTeam("zombies"); // zombies팀 불러오기

        if (gameStart) {
            Bukkit.getScheduler().cancelTasks(this);

            // 게임 종료 후 해당 월드에서 중앙 상자를 제거
            World world = Bukkit.getWorld(worldName);
            if (centralChestLocation != null && world != null) {
                Block block = world.getBlockAt(centralChestLocation);
                if (block.getType() == Material.CHEST) {
                    block.setType(Material.AIR);
                }
            }

            // 플레이어 처리
            for (Player player : Bukkit.getOnlinePlayers()) {
                // 플레이어의 탭 목록 이름을 원래대로 복구
                player.setPlayerListName(player.getName());

                player.closeInventory();
                player.getInventory().clear();
                player.setMaxHealth(20);
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                player.setFoodLevel(20);
                player.setSaturation(20.0f);
                player.setExp(0);
                player.setLevel(0);
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }

                player.setGameMode(GameMode.ADVENTURE);

                Location randomLocation = new Location(world, randomX, world.getHighestBlockYAt(randomX, randomZ) + 3, randomZ);
                player.teleport(randomLocation);
                player.setBedSpawnLocation(randomLocation);

                zombieItemCommand.cooldowns.remove(player.getUniqueId());
            }

            // 좀비 팀 스코어보드 제거 명령
            if(zombieTeamSCB != null) {
                zombieTeamSCB.unregister();
            }

            lastDamager.clear();
            lastDamageTime.clear();

            zombieTeam.clear();
            playerTeam.clear();

            gameStart = false;
            readyShop = false;
            isReady = false;
        }
    }
}
