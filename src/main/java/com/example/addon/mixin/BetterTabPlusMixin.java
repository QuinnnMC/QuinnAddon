package com.example.addon.mixin;

import com.example.addon.modules.BetterTabPlus;

import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(PlayerListHud.class)
public class BetterTabPlusMixin {

    // ============================================================
    // LAYOUT CONSTANTS
    // ============================================================

    private static final int TOP = 32;
    private static final int HEADER_HEIGHT = 12;
    private static final int ROW_HEIGHT = 10;
    private static final int SCREEN_MARGIN = 8;
    private static final int MIN_COLUMN_WIDTH = 90;

    // ============================================================
    // RANK DETECTION
    // ============================================================

    private static final Pattern OWNER_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])owner(?![a-z0-9])");

    private static final Pattern LEGEND_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])legend(?![a-z0-9])");

    private static final Pattern APEX_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])apex(?![a-z0-9])");

    private static final Pattern YOUTUBER_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])(?:youtuber|youtube)(?![a-z0-9])");

    private static final Pattern ELITE_PLUS_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])elite\\s*(?:\\+|plus)(?![a-z0-9])");

    private static final Pattern ELITE_ULTRA_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])elite\\s+ultra(?![a-z0-9])");

    private static final Pattern ELITE_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])elite(?![a-z0-9])");

    private static final Pattern PRIME_ULTRA_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])prime\\s+ultra(?![a-z0-9])");

    private static final Pattern PRIME_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])prime(?![a-z0-9])");

    // ============================================================
    // TAB RENDER
    // ============================================================

    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true
    )
    private void renderBetterTab(
        DrawContext context,
        int width,
        Scoreboard scoreboard,
        ScoreboardObjective objective,
        CallbackInfo ci
    ) {
        BetterTabPlus module =
            Modules.get().get(BetterTabPlus.class);

        if (module == null || !module.isActive()) {
            return;
        }

        MinecraftClient mc =
            MinecraftClient.getInstance();

        if (
            mc.player == null
                || mc.world == null
                || mc.getNetworkHandler() == null
        ) {
            ci.cancel();
            return;
        }

        // ========================================================
        // GET PLAYERS
        // ========================================================

        List<PlayerListEntry> players =
            new ArrayList<>(
                mc.getNetworkHandler().getPlayerList()
            );

        if (players.isEmpty()) {
            ci.cancel();
            return;
        }

        int totalPlayerCount =
            players.size();

        // ========================================================
        // SORT PLAYERS
        // ========================================================

        players.sort((a, b) -> {
            int rankA = getRankPriority(a);
            int rankB = getRankPriority(b);

            if (rankA != rankB) {
                return Integer.compare(rankA, rankB);
            }

            return a.getProfile().name()
                .compareToIgnoreCase(
                    b.getProfile().name()
                );
        });

        // ========================================================
        // MAX PLAYERS
        // ========================================================

        int maxPlayers =
            Math.min(
                module.maxPlayers.get(),
                players.size()
            );

        players =
            new ArrayList<>(
                players.subList(0, maxPlayers)
            );

        // ========================================================
        // SCALE
        // ========================================================

        float scale =
            module.scale.get().floatValue() / 100.0f;

        if (scale <= 0.0f) {
            scale = 1.0f;
        }

        context.getMatrices().pushMatrix();

        context.getMatrices().scale(
            scale,
            scale
        );

        // ========================================================
        // SCALED SCREEN SIZE
        // ========================================================

        int scaledWidth =
            (int) (width / scale);

        int scaledHeight =
            (int) (
                mc.getWindow().getScaledHeight()
                    / scale
            );

        // ========================================================
        // AVAILABLE SPACE
        // ========================================================

        int availableWidth =
            Math.max(
                MIN_COLUMN_WIDTH,
                scaledWidth - (SCREEN_MARGIN * 2)
            );

        int availableHeight =
            Math.max(
                ROW_HEIGHT,
                scaledHeight - TOP - SCREEN_MARGIN
            );

        int maximumRowsThatFit =
            Math.max(
                1,
                availableHeight / ROW_HEIGHT
            );

        // ========================================================
        // INITIAL ROW COUNT
        // ========================================================

        int rows =
            Math.min(
                module.columnHeight.get(),
                maximumRowsThatFit
            );

        rows =
            Math.max(
                1,
                Math.min(
                    rows,
                    players.size()
                )
            );

        // ========================================================
        // INITIAL COLUMN COUNT
        // ========================================================

        int columns =
            (int) Math.ceil(
                (double) players.size() / rows
            );

        columns =
            Math.max(
                1,
                columns
            );

        // ========================================================
        // MAXIMUM COLUMNS
        // ========================================================

        int maxColumnsThatFit =
            Math.max(
                1,
                availableWidth / MIN_COLUMN_WIDTH
            );

        if (columns > maxColumnsThatFit) {
            columns = maxColumnsThatFit;

            rows =
                (int) Math.ceil(
                    (double) players.size() / columns
                );
        }

        // ========================================================
        // VERTICAL FIT
        // ========================================================

        while (
            rows > maximumRowsThatFit
                && columns < maxColumnsThatFit
        ) {
            columns++;

            rows =
                (int) Math.ceil(
                    (double) players.size() / columns
                );
        }

        // ========================================================
        // FINAL GRID
        // ========================================================

        columns =
            Math.max(
                1,
                columns
            );

        rows =
            Math.max(
                1,
                (int) Math.ceil(
                    (double) players.size() / columns
                )
            );

        // ========================================================
        // COLUMN WIDTH
        // ========================================================

        int columnWidth =
            Math.max(
                1,
                availableWidth / columns
            );

        int totalWidth =
            columnWidth * columns;

        int left =
            (scaledWidth - totalWidth) / 2;

        // ========================================================
        // PLAYER COUNT HEADER
        // ========================================================

        if (module.showPlayerCount.get()) {
            String playerCountText =
                totalPlayerCount + " Players";

            int playerCountWidth =
                mc.textRenderer.getWidth(
                    playerCountText
                );

            int playerCountX =
                (scaledWidth - playerCountWidth) / 2;

            context.drawTextWithShadow(
                mc.textRenderer,
                playerCountText,
                playerCountX,
                TOP - HEADER_HEIGHT,
                0xFFFFFFFF
            );
        }

        // ========================================================
        // RENDER PLAYERS
        // ========================================================

        for (
            int i = 0;
            i < players.size();
            i++
        ) {
            PlayerListEntry entry =
                players.get(i);

            int column =
                i / rows;

            int row =
                i % rows;

            int x =
                left + column * columnWidth;

            int y =
                TOP + row * ROW_HEIGHT;

            renderPlayer(
                context,
                mc,
                module,
                entry,
                x,
                y,
                columnWidth
            );
        }

        context.getMatrices().popMatrix();

        // Cancel vanilla tab.
        ci.cancel();
    }

    // ============================================================
    // PLAYER RENDERING
    // ============================================================

    private static void renderPlayer(
        DrawContext context,
        MinecraftClient mc,
        BetterTabPlus module,
        PlayerListEntry entry,
        int x,
        int y,
        int width
    ) {
        int right =
            x + width - 2;

        // Background
        context.fill(
            x,
            y,
            right,
            y + ROW_HEIGHT,
            0x55000000
        );

        String playerName =
            entry.getProfile().name();

        // Rank
        String rank =
            getRank(entry);

        MutableText rankComponent =
            getRankText(
                rank,
                module.simpleRanks.get()
            );

        // Username
        MutableText name =
            Text.literal(playerName);

        name.setStyle(
            name.getStyle().withColor(
                getUsernameColor(rank)
            )
        );

        // Self
        boolean self =
            mc.player != null
                && mc.player.getUuid().equals(
                    entry.getProfile().id()
                );

        // Friend
        boolean friend =
            Friends.get().isFriend(entry);

        if (
            self
                && module.highlightSelf.get()
        ) {
            Color color =
                module.selfColor.get();

            name.setStyle(
                name.getStyle().withColor(
                    color.getPacked()
                )
            );
        }

        else if (
            friend
                && module.highlightFriends.get()
        ) {
            name.setStyle(
                name.getStyle().withColor(
                    0xFF55FF55
                )
            );
        }

        // ========================================================
        // PING
        // ========================================================

        String pingText =
            "";

        if (module.showPing.get()) {
            pingText =
                entry.getLatency() + "ms";
        }

        int pingWidth =
            module.showPing.get()
                ? mc.textRenderer.getWidth(pingText)
                : 0;

        int pingX =
            right - pingWidth - 4;

        // ========================================================
        // GAMEMODE
        // ========================================================

        String gamemode =
            module.showGamemode.get()
                ? getGamemode(entry)
                : "";

        int gamemodeWidth =
            module.showGamemode.get()
                ? mc.textRenderer.getWidth(gamemode)
                : 0;

        int gamemodeX =
            pingX - gamemodeWidth - 6;

        int nameRight;

        if (module.showGamemode.get()) {
            nameRight =
                gamemodeX - 4;
        }

        else if (module.showPing.get()) {
            nameRight =
                pingX - 4;
        }

        else {
            nameRight =
                right - 4;
        }

        int availableNameWidth =
            Math.max(
                10,
                nameRight - x - 3
            );

        // ========================================================
        // RANK WIDTH
        // ========================================================

        int rankWidth =
            mc.textRenderer.getWidth(
                rankComponent
            );

        // ========================================================
        // USERNAME WIDTH
        // ========================================================

        int usernameWidth =
            Math.max(
                1,
                availableNameWidth - rankWidth
            );

        // Trim username only
        MutableText trimmedName =
            trimUsername(
                mc,
                name,
                usernameWidth
            );

        // Draw rank
        context.drawTextWithShadow(
            mc.textRenderer,
            rankComponent,
            x + 2,
            y,
            0xFFFFFFFF
        );

        // Draw username
        int usernameX =
            x + 2 + rankWidth;

        context.drawTextWithShadow(
            mc.textRenderer,
            trimmedName,
            usernameX,
            y,
            0xFFFFFFFF
        );

        // Draw ping
        if (module.showPing.get()) {
            int ping =
                entry.getLatency();

            context.drawTextWithShadow(
                mc.textRenderer,
                pingText,
                pingX,
                y,
                getPingColor(ping)
            );
        }

        // Draw gamemode
        if (module.showGamemode.get()) {
            context.drawTextWithShadow(
                mc.textRenderer,
                gamemode,
                gamemodeX,
                y,
                0xFFFFFFFF
            );
        }
    }

    // ============================================================
    // RANK TEXT
    // ============================================================

    private static MutableText getRankText(
        String rank,
        boolean simple
    ) {
        return switch (rank) {

            // OWNER
            case "owner" -> {
                String text =
                    simple ? "[Ow]" : "[OWNER]";

                MutableText result =
                    Text.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFF5555
                    )
                );

                yield result;
            }

            // LEGEND
            case "legend" -> {
                String text =
                    simple ? "[L]" : "[Legend]";

                MutableText result =
                    Text.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFFF55
                    )
                );

                yield result;
            }

            // APEX
            case "apex" -> {
                String text =
                    simple ? "[A]" : "[Apex]";

                MutableText result =
                    Text.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFAA00
                    )
                );

                yield result;
            }

            // YOUTUBER
            case "youtuber" -> {
                MutableText result =
                    Text.literal("[");

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFFFFF
                    )
                );

                String first =
                    simple ? "Y" : "You";

                String second =
                    simple ? "T]" : "Tuber]";

                MutableText you =
                    Text.literal(first);

                you.setStyle(
                    you.getStyle().withColor(
                        0xFFFF5555
                    )
                );

                MutableText tuber =
                    Text.literal(second);

                tuber.setStyle(
                    tuber.getStyle().withColor(
                        0xFFAAAAAA
                    )
                );

                result.append(you);
                result.append(tuber);

                yield result;
            }

            // ELITE+
            case "elite+" -> {
                String text =
                    simple ? "[E+]" : "[Elite+]";

                MutableText result =
                    Text.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFF55AAFF
                    )
                );

                yield result;
            }

            // ELITE ULTRA
            case "elite ultra" -> {
                MutableText result =
                    Text.literal("[");

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFFFFF
                    )
                );

                String elite =
                    simple ? "E" : "Elite";

                String ultra =
                    simple ? "u]" : " Ultra]";

                MutableText eliteText =
                    Text.literal(elite);

                eliteText.setStyle(
                    eliteText.getStyle().withColor(
                        0xFFFFAA00
                    )
                );

                MutableText ultraText =
                    Text.literal(ultra);

                ultraText.setStyle(
                    ultraText.getStyle().withColor(
                        0xFFFF5555
                    )
                );

                result.append(eliteText);
                result.append(ultraText);

                yield result;
            }

            // ELITE
            case "elite" -> {
                String text =
                    simple ? "[E]" : "[Elite]";

                MutableText result =
                    Text.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFFF55
                    )
                );

                yield result;
            }

            // PRIME ULTRA
            case "prime ultra" -> {
                MutableText result =
                    Text.literal("[");

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFFFFF
                    )
                );

                String prime =
                    simple ? "P" : "Prime";

                String ultra =
                    simple ? "u]" : " Ultra]";

                MutableText primeText =
                    Text.literal(prime);

                primeText.setStyle(
                    primeText.getStyle().withColor(
                        0xFF55AAFF
                    )
                );

                MutableText ultraText =
                    Text.literal(ultra);

                ultraText.setStyle(
                    ultraText.getStyle().withColor(
                        0xFFFF5555
                    )
                );

                result.append(primeText);
                result.append(ultraText);

                yield result;
            }

            // PRIME - CYAN RGB(0, 255, 255)
            case "prime" -> {
                String text =
                    simple ? "[P]" : "[Prime]";

                MutableText result =
                    Text.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFF00FFFF
                    )
                );

                yield result;
            }

            default ->
                Text.empty();
        };
    }

    // ============================================================
    // USERNAME COLOR
    // ============================================================

    private static int getUsernameColor(
        String rank
    ) {
        return switch (rank) {

            // OWNER - WHITE
            case "owner" ->
                0xFFFFFFFF;

            // LEGEND - WHITE
            case "legend" ->
                0xFFFFFFFF;

            // APEX - ORANGE
            case "apex" ->
                0xFFFFAA00;

            // YOUTUBER - YELLOW
            case "youtuber" ->
                0xFFFFFF55;

            // ELITE+ - WHITE
            case "elite+" ->
                0xFFFFFFFF;

            // ELITE + ELITE ULTRA - CYAN RGB(0, 255, 255)
            case "elite",
                 "elite ultra" ->
                0xFF00FFFF;

            // PRIME ULTRA - BLUE
            case "prime ultra" ->
                0xFF55AAFF;

            // PRIME - WHITE
            case "prime" ->
                0xFFFFFFFF;

            // UNRANKED - RGB(200, 200, 200)
            default ->
                0xFFC8C8C8;
        };
    }

    // ============================================================
    // RANK PRIORITY
    // ============================================================

    private static int getRankPriority(
        PlayerListEntry entry
    ) {
        String rank =
            getRank(entry);

        return switch (rank) {

            case "owner" -> 0;
            case "legend" -> 1;
            case "apex" -> 2;
            case "youtuber" -> 3;
            case "elite+" -> 4;
            case "elite ultra" -> 5;
            case "elite" -> 6;
            case "prime ultra" -> 7;
            case "prime" -> 8;

            default -> 9;
        };
    }

    // ============================================================
    // GET RANK
    // ============================================================

    private static String getRank(
        PlayerListEntry entry
    ) {
        // Scoreboard team prefix
        if (
            entry.getScoreboardTeam() != null
        ) {
            String prefix =
                entry.getScoreboardTeam()
                    .getPrefix()
                    .getString();

            String rank =
                detectRank(prefix);

            if (!rank.isEmpty()) {
                return rank;
            }
        }

        // Display name
        Text displayName =
            entry.getDisplayName();

        if (displayName != null) {
            String display =
                displayName.getString();

            String rank =
                detectRankFromDisplayName(
                    display
                );

            if (!rank.isEmpty()) {
                return rank;
            }
        }

        // Team name
        if (
            entry.getScoreboardTeam() != null
        ) {
            String teamName =
                entry.getScoreboardTeam()
                    .getName();

            String rank =
                detectExactRank(teamName);

            if (!rank.isEmpty()) {
                return rank;
            }
        }

        return "";
    }

    // ============================================================
    // DETECT RANK FROM DISPLAY NAME
    // ============================================================

    private static String detectRankFromDisplayName(
        String text
    ) {
        if (
            text == null
                || text.isEmpty()
        ) {
            return "";
        }

        String trimmed =
            text.trim();

        if (
            startsWithRankPrefix(
                trimmed,
                "owner"
            )
        ) {
            return "owner";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "legend"
            )
        ) {
            return "legend";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "apex"
            )
        ) {
            return "apex";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "youtuber"
            )
            || startsWithRankPrefix(
                trimmed,
                "youtube"
            )
        ) {
            return "youtuber";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "elite+"
            )
            || startsWithRankPrefix(
                trimmed,
                "elite plus"
            )
        ) {
            return "elite+";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "elite ultra"
            )
        ) {
            return "elite ultra";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "elite"
            )
        ) {
            return "elite";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "prime ultra"
            )
        ) {
            return "prime ultra";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "prime"
            )
        ) {
            return "prime";
        }

        return "";
    }

    // ============================================================
    // STARTS WITH RANK PREFIX
    // ============================================================

    private static boolean startsWithRankPrefix(
        String text,
        String rank
    ) {
        String lower =
            text.toLowerCase(
                Locale.ROOT
            );

        String normalizedRank =
            rank.toLowerCase(
                Locale.ROOT
            );

        if (
            lower.startsWith(
                "[" + normalizedRank + "]"
            )
        ) {
            return true;
        }

        if (
            lower.startsWith(
                "<" + normalizedRank + ">"
            )
        ) {
            return true;
        }

        if (
            lower.startsWith(
                "(" + normalizedRank + ")"
            )
        ) {
            return true;
        }

        if (
            lower.startsWith(
                normalizedRank
            )
        ) {
            int length =
                normalizedRank.length();

            if (
                lower.length() == length
            ) {
                return true;
            }

            char next =
                lower.charAt(length);

            return !Character.isLetterOrDigit(
                next
            );
        }

        return false;
    }

    // ============================================================
    // DETECT EXACT RANK
    // ============================================================

    private static String detectExactRank(
        String text
    ) {
        if (
            text == null
                || text.isEmpty()
        ) {
            return "";
        }

        String normalized =
            text.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if (
            normalized.startsWith("[")
                && normalized.endsWith("]")
        ) {
            normalized =
                normalized.substring(
                    1,
                    normalized.length() - 1
                ).trim();
        }

        if (
            normalized.startsWith("<")
                && normalized.endsWith(">")
        ) {
            normalized =
                normalized.substring(
                    1,
                    normalized.length() - 1
                ).trim();
        }

        if (
            normalized.startsWith("(")
                && normalized.endsWith(")")
        ) {
            normalized =
                normalized.substring(
                    1,
                    normalized.length() - 1
                ).trim();
        }

        return switch (normalized) {

            case "owner" ->
                "owner";

            case "legend" ->
                "legend";

            case "apex" ->
                "apex";

            case "youtuber",
                 "youtube" ->
                "youtuber";

            case "elite+",
                 "elite plus" ->
                "elite+";

            case "elite ultra" ->
                "elite ultra";

            case "elite" ->
                "elite";

            case "prime ultra" ->
                "prime ultra";

            case "prime" ->
                "prime";

            default ->
                "";
        };
    }

    // ============================================================
    // DETECT RANK IN PREFIX
    // ============================================================

    private static String detectRank(
        String text
    ) {
        if (
            text == null
                || text.isEmpty()
        ) {
            return "";
        }

        // More specific ranks first.

        if (
            matches(
                ELITE_PLUS_PATTERN,
                text
            )
        ) {
            return "elite+";
        }

        if (
            matches(
                ELITE_ULTRA_PATTERN,
                text
            )
        ) {
            return "elite ultra";
        }

        if (
            matches(
                PRIME_ULTRA_PATTERN,
                text
            )
        ) {
            return "prime ultra";
        }

        if (
            matches(
                OWNER_PATTERN,
                text
            )
        ) {
            return "owner";
        }

        if (
            matches(
                LEGEND_PATTERN,
                text
            )
        ) {
            return "legend";
        }

        if (
            matches(
                APEX_PATTERN,
                text
            )
        ) {
            return "apex";
        }

        if (
            matches(
                YOUTUBER_PATTERN,
                text
            )
        ) {
            return "youtuber";
        }

        if (
            matches(
                ELITE_PATTERN,
                text
            )
        ) {
            return "elite";
        }

        if (
            matches(
                PRIME_PATTERN,
                text
            )
        ) {
            return "prime";
        }

        return "";
    }

    // ============================================================
    // PATTERN MATCH
    // ============================================================

    private static boolean matches(
        Pattern pattern,
        String text
    ) {
        Matcher matcher =
            pattern.matcher(text);

        return matcher.find();
    }

    // ============================================================
    // TRIM USERNAME ONLY
    // ============================================================

    private static MutableText trimUsername(
        MinecraftClient mc,
        MutableText name,
        int maxWidth
    ) {
        if (
            maxWidth <= 0
        ) {
            return Text.empty();
        }

        if (
            mc.textRenderer.getWidth(name)
                <= maxWidth
        ) {
            return name;
        }

        String plain =
            name.getString();

        String suffix =
            "...";

        int suffixWidth =
            mc.textRenderer.getWidth(
                suffix
            );

        int allowed =
            Math.max(
                1,
                maxWidth - suffixWidth
            );

        StringBuilder result =
            new StringBuilder();

        for (
            int i = 0;
            i < plain.length();
            i++
        ) {
            String candidate =
                result.toString()
                    + plain.charAt(i);

            if (
                mc.textRenderer
                    .getWidth(candidate)
                    > allowed
            ) {
                break;
            }

            result.append(
                plain.charAt(i)
            );
        }

        MutableText trimmed =
            Text.literal(
                result + suffix
            );

        trimmed.setStyle(
            name.getStyle()
        );

        return trimmed;
    }

    // ============================================================
    // PING COLOR
    // ============================================================

    private static int getPingColor(
        int ping
    ) {
        if (ping < 75) {
            return 0xFF55FF55;
        }

        if (ping < 150) {
            return 0xFFFFFF55;
        }

        if (ping < 250) {
            return 0xFFFFAA00;
        }

        return 0xFFFF5555;
    }

    // ============================================================
    // GAMEMODE
    // ============================================================

    private static String getGamemode(
        PlayerListEntry entry
    ) {
        if (
            entry.getGameMode() == null
        ) {
            return "?";
        }

        return switch (
            entry.getGameMode()
        ) {
            case SURVIVAL ->
                "Survival";

            case CREATIVE ->
                "Creative";

            case ADVENTURE ->
                "Adventure";

            case SPECTATOR ->
                "Spectator";
        };
    }
}