package me.bristermitten.privatemines.util;

import com.cryptomorin.xseries.XMaterial;

import com.google.common.base.Enums;
import me.bristermitten.privatemines.PrivateMines;
import me.bristermitten.privatemines.config.LangKeys;
import me.bristermitten.privatemines.util.signs.SignMenuFactory;
import me.bristermitten.privatemines.worldedit.WorldEditVector;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static net.md_5.bungee.api.ChatColor.translateAlternateColorCodes;

public final class Util {

    private final Util utils;

    private Util() {
        utils = this;
    }

    public Util getUtil() {
        return utils;
    }

    private static final Set<Player> onlinePlayers = new HashSet<>();
    private static final PrivateMines privateMines = PrivateMines.getPlugin();
    private static final SignMenuFactory signMenuFactory = new SignMenuFactory(privateMines);
    private static final String TAX_KEY = "{tax}";

    public static Vector toBukkitVector(WorldEditVector weVector) {
        return new Vector(weVector.getX(), weVector.getY(), weVector.getZ());
    }

    public static WorldEditVector toWEVector(Vector bukkitVector) {
        return new WorldEditVector(bukkitVector.getX(), bukkitVector.getY(), bukkitVector.getZ());
    }

    public static WorldEditVector toWEVector(Location bukkitVector) {
        return new WorldEditVector(bukkitVector.getX(), bukkitVector.getY(), bukkitVector.getZ());
    }


    public static WorldEditVector deserializeWorldEditVector(Map<String, Object> map) {
        return toWEVector(Vector.deserialize(map));
    }

    public static ItemStack deserializeStack(Map<String, Object> map, Object... placeholders) {
        Map<String, String> replacements = arrayToMap(placeholders);

        ItemStack s = new ItemStack(Material.AIR);
        s.setAmount((int) map.getOrDefault("Amount", 1));
        String type = (String) map.getOrDefault("Type", Material.STONE.name());
        type = replacements.getOrDefault(type, type);
        final Optional<XMaterial> materialOpt = XMaterial.matchXMaterial(type);
        if (!materialOpt.isPresent()) {
            throw new IllegalStateException("Unknown material " + type);
        }
        final XMaterial xMaterial = materialOpt.get();
            if (xMaterial.parseMaterial() != null) {
                s.setType(xMaterial.parseMaterial());
            }

        if (map.containsKey("Data")) {
            final ItemMeta itemMeta = s.getItemMeta();
            final int damage = (int) map.get("Data");
            Damageable im = (Damageable) s.getItemMeta();
            if (itemMeta != null && damage > 0 && im != null) {
                im.setDamage(damage);
            }
        } else {
            if (xMaterial.parseItem() != null && Objects.requireNonNull(xMaterial.parseItem()).hasItemMeta()) {
                    final Damageable im = (Damageable) Objects.requireNonNull(xMaterial.parseItem()).getItemMeta();
                    final int damage = (int) map.get("Data");
                    if (im != null) {
                        im.setDamage(damage);
                    }
                }
            }

        ItemMeta itemMeta = s.getItemMeta();

        if (itemMeta != null) {
            itemMeta.setDisplayName(color((String) map.get("Name")));

            //noinspection unchecked
            itemMeta.setLore(color((List<String>) map.get("Lore")));
        }
        s.setItemMeta(itemMeta);
        return s;
    }

    public static String color(String s) {
        if (s == null) {
            return null;
        }
        return translateAlternateColorCodes('&', s);
    }

    public static List<String> color(List<String> lines) {
        if (lines == null) {
            return Collections.emptyList();
        }

        return lines.stream().map(Util::color).collect(toList());
    }

    public static Map<String, String> arrayToMap(Object... array) {
        if (array.length == 0) {
            return Collections.emptyMap();
        }
        if (array.length % 2 != 0) {
            throw new IllegalArgumentException("Array size must be a multiple of 2");
        }

        Map<String, String> map = new LinkedHashMap<>(array.length / 2);
        int i = 0;
        while (i < array.length - 1) {
            map.put(array[i].toString(), array[i + 1].toString());
            i += 2;
        }
        return map;
    }

    public static void replaceMeta(@NotNull ItemMeta meta, @NotNull Object... replacements) {
        Map<String, String> replace = arrayToMap(replacements);

        if (meta.hasDisplayName()) {
            replace.forEach((k, v) -> meta.setDisplayName(meta.getDisplayName().replace(k, v)));
        }
        if (meta.hasLore()) {
            meta.setLore(Objects.requireNonNull(meta.getLore()).stream().map(line -> {
                String[] mutableLine = {line};

                replace.forEach((k, v) -> mutableLine[0] = mutableLine[0].replace(k, v));

                return mutableLine[0];
            }).collect(toList()));
        }
    }

    public static String prettify(String s) {
        return Arrays.stream(s.split("_"))
                .map(String::toLowerCase)
                .map(str -> str.substring(0, 1).toUpperCase() + str.substring(1))
                .collect(Collectors.joining(" "));
    }


    public static Optional<String> getItemName(ItemStack s) {
        try {
            final XMaterial xMaterial = XMaterial.matchXMaterial(s);
            return Optional.of(prettify(xMaterial.name()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static Float getYaw(BlockFace face) {
        switch (face) {
            case WEST:
                return 90f;
            case NORTH:
                return 180f;
            case EAST:
                return -90f;
            case SOUTH:
                return -180f;
            default:
                return 0f;
        }
    }

    /**
     * I hate this function with a passion
     * Format:
     * "materialName" => new ItemStack(valueOf(name))
     * "materialName/durability" => new ItemStack(valueOf(name), 1, durability)
     *
     * @param s the material name (and optional id) to parse
     * @return an Optional ItemStack based on the data, including Material and possibly durability
     */
    public static Optional<ItemStack> parseItem(String s) {
        String materialName = s;
        if (s.contains("/")) {
            final String[] split = s.split("/");
            materialName = split[0];

        }
        //Prioritise native material names
        try {
            return Optional.of(new ItemStack(Material.valueOf(materialName), 1));
        } catch (IllegalArgumentException e1) {
            e1.printStackTrace();
        }
        Optional<XMaterial> m = Optional.ofNullable(Enums.getIfPresent(XMaterial.class, s).orNull());
        if (m.isPresent()) {
            return m.map(XMaterial::parseItem);
        }
        return XMaterial.matchXMaterial(s)
                .map(XMaterial::parseItem);
    }


    public static String parseStyle(String s) {
        switch (s) {
            case "SLOW":
                return "Slow";
            case "FAST":
                return "Fast";
            case "UP_DOWN":
                return "Up down";
            case "DOWN_UP":
                return "Down up";
            case "LEFT_RIGHT":
                return "Left to Right";
            case "RIGHT_LEFT":
                return "Right to Left";
            case "FRONT_BACK":
                return "Front to Back";
            case "BACK_FRONT":
                return "Back to Front";
            default:
                return "Unknown reset Style!";
        }
    }


    public static String parsePercent(Integer integer) {
        DecimalFormat format = new DecimalFormat("##.##");
        return format.format(integer);
    }

    /*
    This is a super basic caching system for the online players saves a tiny bit of milliseconds!
    */

    // Adding a player into the cache system, used mainly in the on player join event

    public static void addToOnlinePlayers(Player player) {
        onlinePlayers.add(player);
    }

    // Remove a player from the cache system, used mainly in the on player leave event

    public static void removeFromOnlinePlayers(Player player) {
        onlinePlayers.remove(player);
    }



    // Gets the online players, this is used in the MineResetTask class so instead of getting
    // all the online players each time possibly causing a tiny spike in the server
    // it will get it from this cache!

    public static Set<Player> getOnlinePlayers() {
        return onlinePlayers;
    }

    public static void openTaxSign(Player player) {

        ArrayList<String> signLines = new ArrayList<>();

        signLines.add("Please enter");
        signLines.add("Tax Amount:");

        SignMenuFactory.Menu menu = signMenuFactory
                .newMenu(signLines)
                .reopenIfFail(false)
                .response((pl, strings) -> {
                    if (!strings[2].matches("^[0-9][0-9]?$|^100$")) {
                        pl.sendMessage(ChatColor.RED + "You didn't specify a tax amount between 1 -> 100!");
                        return false;
                    }
                    double newTax = Double.parseDouble(strings[2]);
                    privateMines.getManager().getCommandIssuer(pl).sendInfo(LangKeys.INFO_TAX_SET, TAX_KEY, String.valueOf(newTax));
                    pl.sendMessage("New tax amount: " + newTax);
                    return true;
                });
        menu.open(player);
    }
}
