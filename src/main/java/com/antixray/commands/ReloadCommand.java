package com.antixray.commands;

import com.antixray.AntiXrayPlugin;
import com.antixray.config.ConfigurationManager;
import com.antixray.config.WorldConfig;
import com.antixray.engine.ObfuscationEngine;
import com.antixray.permissions.PermissionConstants;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.logging.Level;

/**
 * Handles {@code /antixray reload}.
 *
 * <p>Reload sequence:</p>
 * <ol>
 * <li>Call {@link ConfigurationManager#reload()} which re-reads config.yml,
 * re-validates, and rebuilds all {@link WorldConfig} instances.
 * If engine-mode or hidden-blocks changed, the configuration manager
 * fires {@link com.antixray.config.ConfigChangeListener}s which
 * call {@link com.antixray.cache.ObfuscationCache#invalidateAll()}.</li>
 * <li>Update the {@link ObfuscationEngine} with the new global config values
 * (engine mode, max-block-height, deepslate-below-y, fake-ore-chance,
 * server-salt).</li>
 * </ol>
 */
public class ReloadCommand implements SubCommand {

    private static final String HEADER = ChatColor.GOLD + "[AntiXray] " + ChatColor.RESET;

    private final AntiXrayPlugin plugin;

    public ReloadCommand(AntiXrayPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public List<String> aliases() {
        return List.of("rl");
    }

    @Override
    public String permission() {
        return PermissionConstants.RELOAD;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        try {
            ConfigurationManager configMgr = plugin.getConfigurationManager();
            WorldConfig oldGlobal = configMgr.getGlobalConfig();
            var oldMode = oldGlobal != null ? oldGlobal.getEngineMode() : null;
            var oldBlocks = oldGlobal != null ? oldGlobal.getHiddenBlocks() : null;

            configMgr.reload();

            WorldConfig newGlobal = configMgr.getGlobalConfig();

            ObfuscationEngine engine = plugin.getObfuscationEngine();
            if (engine != null) {
                engine.setEngineMode(newGlobal.getEngineMode());
                engine.setMaxBlockHeight(newGlobal.getMaxBlockHeight());
                engine.setDeepslateBelowY(newGlobal.getDeepslateBelowY());
                engine.setFakeOreChance(newGlobal.getFakeOreChance());
                engine.setServerSalt(plugin.getConfig().getLong("server-salt", 0L));
            }

            boolean modeChanged = oldMode != null && oldMode != newGlobal.getEngineMode();
            boolean blocksChanged = oldBlocks != null && !oldBlocks.equals(newGlobal.getHiddenBlocks());

            String prefix = getMessage("prefix", "&8[&bAntiXray&8] &r", sender);
            sender.sendMessage(prefix + getMessage("reload-success", "&aConfiguration reloaded successfully.", sender));
            if (modeChanged || blocksChanged) {
                sender.sendMessage(prefix + getMessage("reload-cache-cleared", "&7Cache cleared due to configuration changes.", sender));
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload configuration", e);
            String prefix = getMessage("prefix", "&8[&bAntiXray&8] &r", sender);
            sender.sendMessage(prefix + getMessage("reload-fail", "&cFailed to reload configuration. Check console for errors.", sender));
        }
    }

    private String getMessage(String key, String def, CommandSender sender) {
        if (plugin.getI18n() != null) {
            return plugin.getI18n().getMessage(key, sender);
        }
        String msg = def;
        if (plugin.getConfigurationManager() != null) {
            msg = plugin.getConfigurationManager().getMessage(key, def);
        }
        if (msg == null) {
            msg = def;
        }
        if (msg == null) {
            msg = "";
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }
}
