package com.tesant.datapackcompat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class DatapackCompat implements ModInitializer {
    public static final String MOD_ID = "datapackcompat";

    private record Finding(Path file, int line, String category, String text) {}

    private static final List<Rule> RULES = List.of(
            new Rule("Legacy gamerule", Pattern.compile("\\b(doFireTick|allowFireTicksAwayFromPlayer|announceAdvancements|commandBlocksEnabled|disableElytraMovementCheck|commandModificationBlockLimit|spawnChunkRadius|doVinesSpread|snowAccumulationHeight|playersSleepingPercentage)\\b")),
            new Rule("Legacy item NBT", Pattern.compile("\\b(tag|Enchantments|StoredEnchantments|display|CustomModelData|HideFlags)\\s*[:=]")),
            new Rule("Old item stack fields", Pattern.compile("\\b(Count|Damage)\\s*[:=]")),
            new Rule("Legacy predicate layout", Pattern.compile("\\b(enchantments|stored_enchantments|potion_contents)\\s*[:=]")),
            new Rule("Likely legacy entity data", Pattern.compile("\\b(Motion|Rotation|Pos)\\s*[:=]")),
            new Rule("Old atlas reference", Pattern.compile("blocks[/\\]atlas|\\bblocks_atlas\\b"))
    );

    private record Rule(String category, Pattern pattern) {}

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("datapackcompat")
                .requires(source -> source.hasPermissionLevel(2));

        root.then(CommandManager.literal("scan").executes(ctx -> scan(ctx.getSource())));
        root.then(CommandManager.literal("version").executes(ctx -> {
            ctx.getSource().sendFeedback(() -> Text.literal("Datapack Compat 0.1.0 — Minecraft 1.21.11"), false);
            return 1;
        }));

        dispatcher.register(root);
    }

    private static int scan(ServerCommandSource source) {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path saves = gameDir.resolve("saves");
        if (!Files.isDirectory(saves)) {
            source.sendError(Text.literal("No .minecraft/saves directory was found."));
            return 0;
        }

        List<Finding> findings = new ArrayList<>();
        int filesScanned = 0;

        try (Stream<Path> worlds = Files.list(saves)) {
            for (Path world : worlds.filter(Files::isDirectory).toList()) {
                Path datapacks = world.resolve("datapacks");
                if (!Files.isDirectory(datapacks)) continue;

                try (Stream<Path> files = Files.walk(datapacks)) {
                    for (Path file : files.filter(Files::isRegularFile)
                            .filter(DatapackCompat::isTextFile).toList()) {
                        filesScanned++;
                        scanFile(file, findings);
                    }
                }
            }
        } catch (IOException e) {
            source.sendError(Text.literal("Scan failed: " + e.getMessage()));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Datapack Compat scanned " + filesScanned + " text files."), false);
        if (findings.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No known legacy patterns were found."), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("Found " + findings.size() + " possible compatibility issues. Details are in latest.log."), false);
        for (Finding f : findings) {
            System.out.println("[DatapackCompat] " + f.category + " | " + f.file + ":" + f.line + " | " + f.text.trim());
        }
        return 1;
    }

    private static boolean isTextFile(Path p) {
        String s = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return s.endsWith(".mcfunction") || s.endsWith(".json") || s.endsWith(".mcmeta") || s.endsWith(".jsonc") || s.endsWith(".txt");
    }

    private static void scanFile(Path file, List<Finding> findings) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                for (Rule rule : RULES) {
                    if (rule.pattern.matcher(line).find()) {
                        findings.add(new Finding(file, i + 1, rule.category, line));
                        break;
                    }
                }
            }
        } catch (IOException ignored) {
            System.out.println("[DatapackCompat] Could not read " + file);
        }
    }
}
