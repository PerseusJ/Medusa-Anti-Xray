package com.antixray.commands;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Contract for an {@code /antixray} sub-command.
 *
 * <p>Each sub-command (reload, stats, check, …) implements this interface.
 * The root {@link AntiXrayCommand} delegates execution and tab-completion
 * to the matching sub-command instance.</p>
 */
public interface SubCommand {

    /**
     * The literal name used after {@code /antixray}.
     *
     * <p>Must be lowercase and contain no spaces (e.g. {@code "reload"}).</p>
     *
     * @return the sub-command label
     */
    String name();

    /**
     * Optional short aliases for this sub-command.
     *
     * @return immutable list of aliases (empty if none)
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * The permission node required to run this sub-command.
     *
     * @return a permission string, or empty string if no permission is needed
     */
    String permission();

    /**
     * Execute the sub-command.
     *
     * @param sender the command sender
     * @param args   arguments after the sub-command label (may be empty)
     */
    void execute(CommandSender sender, String[] args);

    /**
     * Provide tab-completions for arguments after the sub-command label.
     *
     * @param sender the command sender
     * @param args   arguments already typed after the sub-command label
     * @return immutable list of completions (empty if none)
     */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
