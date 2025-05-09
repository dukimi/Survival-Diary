package org.gacstudio.zomSur;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

public class dataLoad {
    private final survivalDiary mainPg;

    public Material winItem;

    public dataLoad(survivalDiary plugin) {
        this.mainPg = plugin;
    }

    public void loadConfig() {
        // 설정 파일이 존재하지 않으면 기본 설정 파일을 생성
        if (!new File(mainPg.getDataFolder(), "config.yml").exists()) {
            mainPg.saveDefaultConfig();
        }

        // 기본값 다이아몬드
        String itemName = mainPg.getConfig().getString("win-item");
        try {
            winItem = Material.valueOf(itemName);
        } catch (IllegalArgumentException e) {
            mainPg.getLogger().warning("The set victory item is invalid. The default value (DIAMOND) will be used.");
            winItem = Material.DIAMOND;
        }
    }
    public String getWorldNameFromConfig() {
        // 기본값으로 config.yml 파일 세계 이름 가져오기
        FileConfiguration config = mainPg.getConfig();
        mainPg.worldName = config.getString("worldName");

        // config.yml 에서 worldName 이 비어있다면 서버의 첫 번째 세계(기본 세계값)를 가져와서 이름을 설정
        if (mainPg.worldName == null) {
            mainPg.worldName = Bukkit.getWorlds().getFirst().getName();
        }

        return mainPg.worldName;
    }
}
