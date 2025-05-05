package org.gacstudio.zomSur;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

public class dataLoad {
    private final zombieSurvival zombieSurvival;

    public Material winItem;

    public dataLoad(zombieSurvival plugin) {
        this.zombieSurvival = plugin;
    }

    public void loadConfig() {
        // 설정 파일이 존재하지 않으면 기본 설정 파일을 생성
        if (!new File(zombieSurvival.getDataFolder(), "config.yml").exists()) {
            zombieSurvival.saveDefaultConfig();
        }

        // 기본값 다이아몬드
        String itemName = zombieSurvival.getConfig().getString("win-item");
        try {
            winItem = Material.valueOf(itemName);
        } catch (IllegalArgumentException e) {
            zombieSurvival.getLogger().warning("The set victory item is invalid. The default value (DIAMOND) will be used.");
            winItem = Material.DIAMOND;
        }
    }
    public String getWorldNameFromConfig() {
        // 기본값으로 config.yml 파일 세계 이름 가져오기
        FileConfiguration config = zombieSurvival.getConfig();
        zombieSurvival.worldName = config.getString("worldName");

        // config.yml 에서 worldName 이 비어있다면 서버의 첫 번째 세계(기본 세계값)를 가져와서 이름을 설정
        if (zombieSurvival.worldName == null) {
            zombieSurvival.worldName = Bukkit.getWorlds().getFirst().getName();
        }

        return zombieSurvival.worldName;
    }
}