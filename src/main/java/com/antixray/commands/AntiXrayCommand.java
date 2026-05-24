package com.antixray.commands;

import com.antixray.AntiXrayPlugin;
import com.antixray.permissions.PermissionConstants;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Root command handler for {@code /antixray}.
 *
 * <p>Dispatches to registered {@link SubCommand} instances based on the first
 * argument. Provides tab-completion for sub-command names and delegates further
 * completion to each sub-command's own logic.</p>
 *
 * <p>Sub-commands are registered via {@link #registerSubCommand(SubCommand)}.
 * Only sub-commands whose {@link SubCommand#permission() required permission}
 * is held by the sender appear in tab-completion and are executable.</p>
 */
public class AntiXrayCommand implements TabExecutor {

    private static final String HEADER = ChatColor.GOLD + "[AntiXray] " + ChatColor.RESET;

    private final AntiXrayPlugin plugin;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public AntiXrayCommand(AntiXrayPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register a sub-command. Its {@link SubCommand#name()} and each of its
     * {@link SubCommand#aliases() aliases} become dispatch keys.
     *
     * @param sub the sub-command to register
     */
    public void registerSubCommand(SubCommand sub) {
        subCommands.put(sub.name().toLowerCase(Locale.ROOT), sub);
        for (String alias : sub.aliases()) {
            subCommands.put(alias.toLowerCase(Locale.ROOT), sub);
        }
    }

    // ── Execution ────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subName = args[0].toLowerCase(Locale.ROOT);
        SubCommand sub = subCommands.get(subName);

        String prefix = getMessage("prefix", "&8[&bAntiXray&8] &r", sender);

        if (sub == null) {
            sender.sendMessage(prefix + getMessage("unknown-subcommand", "&cUnknown subcommand. Use /antixray help for a list.", sender));
            sendUsage(sender);
            return true;
        }

        // Permission check
        String perm = sub.permission();
        if (!perm.isEmpty() && !sender.hasPermission(perm)) {
            sender.sendMessage(prefix + getMessage("no-permission", "&cYou do not have permission to use this command.", sender));
            return true;
        }

        // Pass remaining args (after sub-command name) to the handler
        String[] subArgs = new String[args.length - 1];
        if (subArgs.length > 0) {
            System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        }

        sub.execute(sender, subArgs);
        return true;
    }

    // ── Tab completion ───────────────────────────────────────────────────

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {

        if (args.length == 0) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // Complete sub-command name
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();

            // Define all subcommands with their permissions
            Map<String, String> subPerms = new LinkedHashMap<>();
            subPerms.put("reload", PermissionConstants.RELOAD);
            subPerms.put("stats", PermissionConstants.STATS);
            subPerms.put("check", PermissionConstants.CHECK);
            subPerms.put("mode", PermissionConstants.MODE);
            subPerms.put("cache", PermissionConstants.CACHE);
            subPerms.put("toggle", PermissionConstants.TOGGLE);
            subPerms.put("status", PermissionConstants.ADMIN);
            subPerms.put("timings", PermissionConstants.ADMIN);

            for (Map.Entry<String, String> entry : subPerms.entrySet()) {
                String name = entry.getKey();
                String perm = entry.getValue();

                // Check if canonical name matches prefix, or is reload alias
                boolean matchesPrefix = name.toLowerCase(Locale.ROOT).startsWith(prefix);
                if (name.equals("reload") && "rl".startsWith(prefix)) {
                    matchesPrefix = true;
                }

                if (matchesPrefix) {
                    if (perm.isEmpty() || sender.hasPermission(perm)) {
                        matches.add(name);
                    }
                }
            }
            return matches;
        }

        String subName = args[0].toLowerCase(Locale.ROOT);

        // If it's a registered subcommand, attempt to delegate
        SubCommand sub = subCommands.get(subName);
        if (sub != null) {
            String perm = sub.permission();
            if (!perm.isEmpty() && !sender.hasPermission(perm)) {
                return Collections.emptyList();
            }
            String[] subArgs = new String[args.length - 1];
            System.arraycopy(args, 1, subArgs, 0, subArgs.length);
            List<String> delegated = sub.tabComplete(sender, subArgs);
            if (delegated != null && !delegated.isEmpty()) {
                return delegated;
            }
        }

        // Fallback static tab-completion for all blueprint commands (reload, stats, check, mode, cache, toggle, status, timings)
        String requiredPerm = getRequiredPermission(subName);
        if (requiredPerm == null || (!requiredPerm.isEmpty() && !sender.hasPermission(requiredPerm))) {
            return Collections.emptyList();
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (subName.equals("stats") || subName.equals("check")) {
            if (args.length == 2) {
                List<String> completions = new ArrayList<>();
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(p.getName());
                    }
                }
                return completions;
            }
        } else if (subName.equals("mode")) {
            if (args.length == 2) {
                List<String> modes = List.of("1", "2", "3");
                List<String> completions = new ArrayList<>();
                for (String m : modes) {
                    if (m.startsWith(prefix)) {
                        completions.add(m);
                    }
                }
                return completions;
            } else if (args.length == 3) {
                List<String> completions = new ArrayList<>();
                for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
                    if (w.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(w.getName());
                    }
                }
                return completions;
            }
        } else if (subName.equals("cache")) {
            if (args.length == 2) {
                if ("clear".startsWith(prefix)) {
                    return List.of("clear");
                }
            } else if (args.length == 3 && args[1].equalsIgnoreCase("clear")) {
                List<String> completions = new ArrayList<>();
                for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
                    if (w.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(w.getName());
                    }
                }
                return completions;
            }
        } else if (subName.equals("toggle")) {
            if (args.length == 2) {
                List<String> completions = new ArrayList<>();
                for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
                    if (w.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(w.getName());
                    }
                }
                return completions;
            }
        }

        return Collections.emptyList();
    }

    private @Nullable String getRequiredPermission(String subName) {
        switch (subName) {
            case "reload":
            case "rl":
                return PermissionConstants.RELOAD;
            case "stats":
                return PermissionConstants.STATS;
            case "check":
                return PermissionConstants.CHECK;
            case "mode":
                return PermissionConstants.MODE;
            case "cache":
                return PermissionConstants.CACHE;
            case "toggle":
                return PermissionConstants.TOGGLE;
            case "status":
            case "timings":
                return PermissionConstants.ADMIN;
            default:
                return null;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void sendUsage(CommandSender sender) {
        String prefix = getMessage("prefix", "&8[&bAntiXray&8] &r", sender);
        sender.sendMessage(prefix + getMessage("command-usage", "&eUsage: /antixray <subcommand>", sender));
        sender.sendMessage(getMessage("command-available-subs", "&7Available sub-commands:", sender));

        for (SubCommand sub : subCommands.values()) {
            // Show each canonical name only once
            if (subCommands.get(sub.name()) != sub) continue;

            String perm = sub.permission();
            if (!perm.isEmpty() && !sender.hasPermission(perm)) continue;

            String aliasHint = sub.aliases().isEmpty()
                    ? ""
                    : ChatColor.GRAY + " (aliases: " + String.join(", ", sub.aliases()) + ")";
            sender.sendMessage(ChatColor.WHITE + "  " + sub.name() + aliasHint);
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
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
