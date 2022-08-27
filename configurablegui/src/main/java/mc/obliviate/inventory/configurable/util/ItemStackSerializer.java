package mc.obliviate.inventory.configurable.util;

import com.google.common.base.Preconditions;
import mc.obliviate.inventory.configurable.GuiConfigurationTable;
import mc.obliviate.util.placeholder.PlaceholderUtil;
import mc.obliviate.util.string.StringUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ItemStackSerializer {

    /**
     * This method matches material and material name. Uses <a href="https://github.com/CryptoMorin/XSeries/blob/master/src/main/java/com/cryptomorin/xseries/XMaterial.java">XMaterial</a>
     * at backend.
     * <p>
     * Legacy versions needs material data (aka damage, durability)
     * to recognize some materials. Ex: white wool and red wool are
     * same material for 1.8 servers. That is why this method
     * returns itemstack instead of material.
     *
     * @param section YAML configuration section of item stack
     * @return raw item stack
     */
    @Nonnull
    public static ItemStack deserializeMaterial(@Nonnull ConfigurationSection section) {
        return ItemStackSerializer.deserializeMaterial(section, GuiConfigurationTable.getDefaultConfigurationTable());
    }

    /**
     * This method matches material and material name. Uses <a href="https://github.com/CryptoMorin/XSeries/blob/master/src/main/java/com/cryptomorin/xseries/XMaterial.java">XMaterial</a>
     * at backend.
     * <p>
     * Legacy versions needs material data (aka damage, durability)
     * to recognize some materials. Ex: white wool and red wool are
     * same material for 1.8 servers. That is why this method
     * returns itemstack instead of material.
     *
     * @param section YAML configuration section of item stack
     * @param table   table to find section names
     * @return raw item stack
     */
    @Nonnull
    public static ItemStack deserializeMaterial(@Nonnull ConfigurationSection section, GuiConfigurationTable table) {
        final String materialName = section.getString(table.getMaterialSectionName());
        if (materialName == null) throw new IllegalArgumentException("material section could not find");

        final Optional<XMaterial> xmaterial = XMaterial.matchXMaterial(materialName);
        if (!xmaterial.isPresent()) {
            throw new IllegalArgumentException("Material could not found: " + materialName);
        }

        final ItemStack item = xmaterial.get().parseItem();
        if (item == null) {
            throw new IllegalArgumentException("Material could not parsed as item stack: " + materialName);
        }
        return item;
    }

    /**
     * This method deserialize a configuration as an item stack.
     * This method parses item type, name, lore, amount, durability,
     * enchantment, item flags, custom model data and unbreakability.
     * <p>
     * However, this method does not parse placeholders because this
     * type of itemstack must be raw to caching itemstacks and
     * applying placeholders at runtime.
     *
     * @param section YAML configuration section of item stack
     * @return deserialized item stack.
     */
    @Nonnull
    public static ItemStack deserializeItemStack(@Nonnull ConfigurationSection section) {
        return ItemStackSerializer.deserializeItemStack(section, GuiConfigurationTable.getDefaultConfigurationTable());
    }

    /**
     * This method deserialize a configuration as an item stack.
     * This method parses item type, name, lore, amount, durability,
     * enchantment, item flags, custom model data and unbreakability.
     * <p>
     * However, this method does not parse placeholders because this
     * type of itemstack must be raw to caching itemstacks and
     * applying placeholders at runtime.
     *
     * @param section YAML configuration section of item stack
     * @param table   table to find section names
     * @return deserialized item stack.
     */
    @Nonnull
    public static ItemStack deserializeItemStack(@Nonnull ConfigurationSection section, GuiConfigurationTable table) {
        if (table == null) table = GuiConfigurationTable.getDefaultConfigurationTable();
        Preconditions.checkNotNull(table, "param table and default table cannot be null at same time.");

        final ItemStack item = deserializeMaterial(section, table);
        ItemMeta meta = item.getItemMeta();
        Preconditions.checkNotNull(meta, "item meta cannot be null");

        meta.setDisplayName(section.getString(table.getDisplayNameSectionName()));
        meta.setLore(section.getStringList(table.getLoreSectionName()));
        item.setItemMeta(meta);

        parseColorOfItemStack(item);
        applyEnchantmentsToItemStack(item, deserializeEnchantments(section, table));

        meta = item.getItemMeta();
        if (section.isSet(table.getCustomModelDataSectionName()))
            meta.setCustomModelData(section.getInt(table.getCustomModelDataSectionName()));
        if (section.getBoolean(table.getUnbreakableSectionName()))
            meta.setUnbreakable(true);
        if (section.isSet(table.getDurabilitySectionName()))
            item.setDurability((short) section.getInt(table.getDurabilitySectionName()));
        if (section.getBoolean(table.getGlowSectionName())) {
            meta.getItemFlags().add(ItemFlag.HIDE_ENCHANTS);
            if (meta.getEnchants().isEmpty()) {
                meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
            }
        }
        item.setItemMeta(meta);

        applyItemFlagsToItemStacks(item, deserializeItemFlags(section, table));
        item.setAmount(section.getInt(table.getAmountSectionName(), 1));


        return item;
    }

    public static void applyItemFlagsToItemStacks(@Nonnull ItemStack item, ItemFlag[] itemFlags) {
        ItemMeta meta = item.getItemMeta();
        Preconditions.checkNotNull(itemFlags, "item flags cannot be null");
        Preconditions.checkNotNull(meta, "item meta cannot be null");

        if (itemFlags.length == 0) return;

        for (ItemFlag itemFlag : itemFlags) {
            if (itemFlag == null) continue;
            meta.addItemFlags(itemFlag);
        }
        item.setItemMeta(meta);
    }

    public static ItemFlag[] deserializeItemFlags(@Nonnull ConfigurationSection section) {
        return ItemStackSerializer.deserializeItemFlags(section, GuiConfigurationTable.getDefaultConfigurationTable());
    }

    public static ItemFlag[] deserializeItemFlags(@Nonnull ConfigurationSection section, GuiConfigurationTable table) {
        if (table == null) table = GuiConfigurationTable.getDefaultConfigurationTable();
        Preconditions.checkNotNull(table, "param table and default table cannot be null at same time.");

        ItemFlag[] itemFlags = new ItemFlag[ItemFlag.values().length];

        List<String> serializedItemFlags = section.getStringList(table.getItemFlagsSectionName());
        if (serializedItemFlags.isEmpty()) return itemFlags;
        if (serializedItemFlags.contains("*")) return ItemFlag.values();

        int index = 0;
        for (String serializedItemFlag : serializedItemFlags) {
            try {
                ItemFlag itemFlag = ItemFlag.valueOf(serializedItemFlag);
                Preconditions.checkNotNull(itemFlag);
                itemFlags[index++] = itemFlag;
            } catch (Exception e) {
                throw new IllegalArgumentException("item flag could not find: " + serializedItemFlag);
            }
        }

        return itemFlags;
    }

    public static void applyEnchantmentsToItemStack(ItemStack item, Map<Enchantment, Integer> enchantments) {
        if (enchantments.isEmpty()) return;
        if (item.getType().equals(XMaterial.ENCHANTED_BOOK.parseMaterial())) {
            final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            if (meta == null) return;

            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                meta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }

            item.setItemMeta(meta);
        } else {
            item.addUnsafeEnchantments(enchantments);
        }
    }

    public static Map<Enchantment, Integer> deserializeEnchantments(@Nonnull ConfigurationSection section) {
        return ItemStackSerializer.deserializeEnchantments(section, GuiConfigurationTable.getDefaultConfigurationTable());
    }

    public static Map<Enchantment, Integer> deserializeEnchantments(@Nonnull ConfigurationSection section, GuiConfigurationTable table) {
        if (!section.isSet(table.getEnchantmentsSectionName())) return new HashMap<>();
        Map<Enchantment, Integer> map = new HashMap<>();
        for (final String serializedEnchantment : section.getStringList(table.getEnchantmentsSectionName())) {
            final Pair<Enchantment, Integer> enchantmentValue = deserializeEnchantment(serializedEnchantment);
            map.put(enchantmentValue.key, enchantmentValue.value);
        }
        return map;
    }

    public static Pair<Enchantment, Integer> deserializeEnchantment(String serializedEnchantment) {
        Preconditions.checkNotNull(serializedEnchantment, "serialized enchantment cannot be null");
        String[] datas = serializedEnchantment.split(":");
        Preconditions.checkArgument(datas.length == 2, "Enchantment could not deserialized: " + serializedEnchantment);
        Enchantment enchantment;
        int value;
        try {
            enchantment = Enchantment.getByName(datas[0]);
            value = Integer.parseInt(datas[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Enchantment or its Value could not resolved: " + datas[0]);
        }
        Preconditions.checkArgument(enchantment != null, "Enchantment could not find: " + datas[0]);
        return new Pair<>(enchantment, value);
    }

    public static void applyPlaceholdersToItemStack(ItemStack item, PlaceholderUtil placeholderUtil) {
        if (item == null) return;
        if (placeholderUtil == null) return;
        final ItemMeta meta = item.getItemMeta();
        Preconditions.checkNotNull(meta, "item meta cannot be null");
        meta.setDisplayName(placeholderUtil.apply(meta.getDisplayName()));
        meta.setLore(placeholderUtil.apply(meta.getLore()));
        item.setItemMeta(meta);
    }

    public static void parseColorOfItemStack(ItemStack item) {
        if (item == null) return;
        final ItemMeta meta = item.getItemMeta();
        Preconditions.checkNotNull(meta, "item meta cannot be null");
        meta.setDisplayName(StringUtil.parseColor(meta.getDisplayName()));
        meta.setLore(StringUtil.parseColor(meta.getLore()));
        item.setItemMeta(meta);
    }

    private static class Pair<K, V> {

        private final K key;
        private final V value;

        private Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

}
