package gg.moonflower.etched.core;

import gg.moonflower.etched.api.sound.download.SoundSourceManager;
import gg.moonflower.etched.client.screen.AlbumJukeboxScreen;
import gg.moonflower.etched.client.screen.EtchingScreen;
import gg.moonflower.etched.client.sound.download.SoundCloudSource;
import gg.moonflower.etched.common.item.EtchedMusicDiscItem;
import gg.moonflower.etched.common.network.EtchedMessages;
import gg.moonflower.etched.core.registry.*;
import gg.moonflower.pollen.api.event.events.entity.ModifyTradesEvents;
import gg.moonflower.pollen.api.event.events.registry.client.RegisterAtlasSpriteEvent;
import gg.moonflower.pollen.api.platform.Platform;
import gg.moonflower.pollen.api.registry.client.*;
import net.minecraft.client.model.MinecartModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * @author Jackson
 * @author Ocelot
 */
public class Etched {

    public static final String MOD_ID = "etched";
    public static final Platform PLATFORM = Platform.builder(Etched.MOD_ID)
            .commonInit(Etched::commonInit)
            .commonPostInit(Etched::commonPostInit)
            .clientInit(Etched::clientInit)
            .clientPostInit(Etched::clientPostInit)
            .build();

    public static void commonInit() {
        EtchedBlocks.BLOCKS.register(Etched.PLATFORM);
        EtchedItems.ITEMS.register(Etched.PLATFORM);
        EtchedMenus.MENUS.register(Etched.PLATFORM);
        EtchedSounds.SOUNDS.register(Etched.PLATFORM);
        EtchedVillagers.POI_TYPES.register(Etched.PLATFORM);
        EtchedVillagers.PROFESSIONS.register(Etched.PLATFORM);

        EtchedMessages.init();

        // TODO: revamp trades
        ModifyTradesEvents.VILLAGER.register(context -> {
            if (context.getProfession() != EtchedVillagers.BARD.get())
                return;

            ModifyTradesEvents.TradeRegistry tier1 = context.getTrades(1);
            tier1.add(Items.MUSIC_DISC_13, 8, 1, 16, 2, true);
            tier1.add(Items.NOTE_BLOCK, 1, 2, 16, 2, true);
            tier1.add(EtchedItems.MUSIC_LABEL, 4, 2, 12, 1, false);

            ModifyTradesEvents.TradeRegistry tier2 = context.getTrades(2);
            tier2.add(EtchedItems.BLANK_MUSIC_DISC, 28, 2, 12, 5, false);
            tier2.add(EtchedBlocks.ETCHING_TABLE, 32, 1, 8, 5, false);

            ModifyTradesEvents.TradeRegistry tier3 = context.getTrades(3);
            tier3.add(Items.JUKEBOX, 26, 1, 12, 10, false);
            tier3.add(Items.MUSIC_DISC_CAT, 24, 1, 16, 20, true);
            tier3.add(Blocks.CLAY, 6, 1, 16, 2, false);
            tier3.add(Blocks.HAY_BLOCK, 12, 1, 8, 2, false);
            tier3.add(Blocks.WHITE_WOOL, 8, 1, 32, 4, false);
            tier3.add(Blocks.BONE_BLOCK, 24, 1, 8, 4, false);
            tier3.add(Blocks.PACKED_ICE, 36, 1, 4, 8, false);
            tier3.add(Blocks.GOLD_BLOCK, 48, 1, 2, 10, false);

            ModifyTradesEvents.TradeRegistry tier4 = context.getTrades(4);
            for (Item item : ItemTags.MUSIC_DISCS.getValues())
                tier4.add(item, 48, 1, 8, 30, true);
            tier4.add(Items.JUKEBOX, 20, 1, 16, 30, true);
            tier4.add(EtchedItems.JUKEBOX_MINECART, 24, 1, 16, 30, true);
            tier4.add(EtchedBlocks.ALBUM_JUKEBOX, 30, 1, 12, 15, false);

            ModifyTradesEvents.TradeRegistry tier5 = context.getTrades(5);
            tier5.add(EtchedItems.BLANK_MUSIC_DISC, 8, 1, 16, 40, true);
            tier5.add(EtchedItems.MUSIC_LABEL, 1, 1, 16, 40, true);
            tier5.add(EtchedBlocks.ETCHING_TABLE, 12, 1, 4, 40, true);
        });
    }

    public static void clientInit() {
        RegisterAtlasSpriteEvent.event(InventoryMenu.BLOCK_ATLAS).register((atlas, registry) -> {
            registry.accept(new ResourceLocation(Etched.MOD_ID, "item/empty_etching_table_slot_music_disc"));
            registry.accept(new ResourceLocation(Etched.MOD_ID, "item/empty_etching_table_slot_music_label"));
        });

        ColorRegistry.register((stack, index) -> index > 0 ? -1 : ((DyeableLeatherItem) stack.getItem()).getColor(stack), EtchedItems.BLANK_MUSIC_DISC, EtchedItems.MUSIC_LABEL);
        ColorRegistry.register((stack, index) -> index == 0 ? EtchedMusicDiscItem.getPrimaryColor(stack) : index == 1 && EtchedMusicDiscItem.getPattern(stack).isColorable() ? EtchedMusicDiscItem.getSecondaryColor(stack) : -1, EtchedItems.ETCHED_MUSIC_DISC);

        ModelLayerLocation layerLocation = new ModelLayerLocation(new ResourceLocation(Etched.MOD_ID, "jukebox_minecart"), "main");
        EntityRendererRegistry.registerLayerDefinition(layerLocation, MinecartModel::createBodyLayer);
        EntityRendererRegistry.register(EtchedEntities.JUKEBOX_MINECART, context -> new MinecartRenderer<>(context, layerLocation));
    }

    public static void commonPostInit(Platform.ModSetupContext ctx) {
        ctx.enqueueWork(EtchedVillagers::registerVillages);
    }

    public static void clientPostInit(Platform.ModSetupContext ctx) {
        SoundSourceManager.registerSource(new SoundCloudSource());

        ctx.enqueueWork(() -> {
            ScreenRegistry.register(EtchedMenus.ETCHING_MENU.get(), EtchingScreen::new);
            ScreenRegistry.register(EtchedMenus.ALBUM_JUKEBOX_MENU.get(), AlbumJukeboxScreen::new);
            RenderTypeRegistry.register(EtchedBlocks.ETCHING_TABLE.get(), RenderType.cutout());
            ItemPredicateRegistry.register(EtchedItems.ETCHED_MUSIC_DISC.get(), new ResourceLocation(Etched.MOD_ID, "pattern"), new ClampedItemPropertyFunction() {
                @Override
                public float call(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int i) {
                    return EtchedMusicDiscItem.getPattern(stack).ordinal(); // :troll:
                }

                @Override
                public float unclampedCall(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int i) {
                    return EtchedMusicDiscItem.getPattern(stack).ordinal();
                }
            });
        });
    }
}
