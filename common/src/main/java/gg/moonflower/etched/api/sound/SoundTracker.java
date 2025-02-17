package gg.moonflower.etched.api.sound;

import gg.moonflower.etched.api.record.PlayableRecord;
import gg.moonflower.etched.api.record.TrackData;
import gg.moonflower.etched.api.sound.source.AudioSource;
import gg.moonflower.etched.api.util.DownloadProgressListener;
import gg.moonflower.etched.common.block.AlbumJukeboxBlock;
import gg.moonflower.etched.common.block.RadioBlock;
import gg.moonflower.etched.common.blockentity.AlbumJukeboxBlockEntity;
import gg.moonflower.etched.core.Etched;
import gg.moonflower.etched.core.mixin.client.GuiAccessor;
import gg.moonflower.etched.core.mixin.client.LevelRendererAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.BaseComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * Tracks entity sounds and all etched playing sounds for the client side.
 *
 * @author Ocelot
 * @since 2.0.0
 */
public class SoundTracker {

    private static final Int2ObjectArrayMap<SoundInstance> ENTITY_PLAYING_SOUNDS = new Int2ObjectArrayMap<>();
    private static final Component RADIO = new TranslatableComponent("sound_source." + Etched.MOD_ID + ".radio");

    /**
     * Retrieves the sound instance for the specified entity id.
     *
     * @param entity The id of the entity to get a sound for
     * @return The sound for that entity
     */
    @Nullable
    public static SoundInstance getEntitySound(int entity) {
        return ENTITY_PLAYING_SOUNDS.get(entity);
    }

    /**
     * Sets the playing sound for the specified entity.
     *
     * @param entity   The id of the entity to play a sound for
     * @param instance The new sound to play or <code>null</code> to stop
     */
    public static void setEntitySound(int entity, @Nullable SoundInstance instance) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (instance == null) {
            SoundInstance old = ENTITY_PLAYING_SOUNDS.remove(entity);
            if (old != null) {
                if (old instanceof StopListeningSound)
                    ((StopListeningSound) old).stopListening();
                soundManager.stop(old);
            }
        } else {
            ENTITY_PLAYING_SOUNDS.put(entity, instance);
            soundManager.play(instance);
        }
    }

    /**
     * Creates an online sound for the specified entity.
     *
     * @param url    The url to play
     * @param title  The title of the record
     * @param entity The entity to play for
     * @param stream Whether to play a stream or regular file
     * @return A new sound instance
     */
    public static AbstractOnlineSoundInstance getEtchedRecord(String url, Component title, Entity entity, boolean stream) {
        return new OnlineRecordSoundInstance(url, entity, new MusicDownloadListener(title, entity::getX, entity::getY, entity::getZ) {
            @Override
            public void onSuccess() {
                if (!entity.isAlive() || !ENTITY_PLAYING_SOUNDS.containsKey(entity.getId())) {
                    this.clearComponent();
                } else {
                    if (PlayableRecord.canShowMessage(entity.getX(), entity.getY(), entity.getZ()))
                        PlayableRecord.showMessage(title);
                }
            }

            @Override
            public void onFail() {
                PlayableRecord.showMessage(new TranslatableComponent("record." + Etched.MOD_ID + ".downloadFail", title));
            }
        }, stream ? AudioSource.AudioFileType.STREAM : AudioSource.AudioFileType.FILE);
    }

    /**
     * Creates an online sound for the specified position.
     *
     * @param url   The url to play
     * @param title The title of the record
     * @param level The level to play the record in
     * @param pos   The position of the record
     * @param type  The type of audio to accept
     * @return A new sound instance
     */
    public static AbstractOnlineSoundInstance getEtchedRecord(String url, Component title, ClientLevel level, BlockPos pos, AudioSource.AudioFileType type) {
        Map<BlockPos, SoundInstance> playingRecords = ((LevelRendererAccessor) Minecraft.getInstance().levelRenderer).getPlayingRecords();
        return new OnlineRecordSoundInstance(url, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new MusicDownloadListener(title, () -> pos.getX() + 0.5, () -> pos.getY() + 0.5, () -> pos.getZ() + 0.5) {
            @Override
            public void onSuccess() {
                if (!playingRecords.containsKey(pos)) {
                    this.clearComponent();
                } else {
                    if (level.getBlockState(pos.above()).isAir() && PlayableRecord.canShowMessage(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
                        PlayableRecord.showMessage(title);
                    if (level.getBlockState(pos).is(Blocks.JUKEBOX))
                        for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(3.0D)))
                            livingEntity.setRecordPlayingNearby(pos, true);
                }
            }

            @Override
            public void onFail() {
                PlayableRecord.showMessage(new TranslatableComponent("record." + Etched.MOD_ID + ".downloadFail", title));
            }
        }, type);
    }

    private static void playRecord(BlockPos pos, SoundInstance sound) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        Map<BlockPos, SoundInstance> playingRecords = ((LevelRendererAccessor) Minecraft.getInstance().levelRenderer).getPlayingRecords();
        playingRecords.put(pos, sound);
        soundManager.play(sound);
    }

    private static void playNextRecord(ClientLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AlbumJukeboxBlockEntity))
            return;

        AlbumJukeboxBlockEntity jukebox = (AlbumJukeboxBlockEntity) blockEntity;
        jukebox.next();
        playAlbum((AlbumJukeboxBlockEntity) blockEntity, level, pos, true);
    }

    public static void playBlockRecord(BlockPos pos, TrackData[] tracks, int track) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
            return;

        if (track >= tracks.length) {
            if (level.getBlockState(pos).is(Blocks.JUKEBOX))
                for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(3.0D)))
                    livingEntity.setRecordPlayingNearby(pos, false);
            return;
        }

        TrackData trackData = tracks[track];
        if (trackData.getUrl() == null || !TrackData.isValidURL(trackData.getUrl())) {
            playBlockRecord(pos, tracks, track + 1);
            return;
        }
        playRecord(pos, StopListeningSound.create(getEtchedRecord(trackData.getUrl(), trackData.getDisplayName(), level, pos, AudioSource.AudioFileType.FILE), () -> Minecraft.getInstance().tell(() -> {
            if (!((LevelRendererAccessor) Minecraft.getInstance().levelRenderer).getPlayingRecords().containsKey(pos))
                return;
            playBlockRecord(pos, tracks, track + 1);
        })));
    }

    /**
     * Plays a record stack for an entity.
     *
     * @param record   The record to play
     * @param entityId The id of the entity to play the record at
     * @param track    The track to play
     * @param loop     Whether to loop
     */
    public static void playEntityRecord(ItemStack record, int entityId, int track, boolean loop) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
            return;

        Entity entity = level.getEntity(entityId);
        if (entity == null)
            return;

        Optional<SoundInstance> sound = ((PlayableRecord) record.getItem()).createEntitySound(record, entity, track);
        if (!sound.isPresent()) {
            if (loop && track != 0)
                playEntityRecord(record, entityId, 0, true);
            return;
        }

        SoundInstance entitySound = ENTITY_PLAYING_SOUNDS.remove(entity.getId());
        if (entitySound != null) {
            if (entitySound instanceof StopListeningSound)
                ((StopListeningSound) entitySound).stopListening();
            Minecraft.getInstance().getSoundManager().stop(entitySound);
        }

        entitySound = StopListeningSound.create(sound.get(), () -> Minecraft.getInstance().tell(() -> {
            ENTITY_PLAYING_SOUNDS.remove(entityId);
            playEntityRecord(record, entityId, track + 1, loop);
        }));

        ENTITY_PLAYING_SOUNDS.put(entityId, entitySound);
        Minecraft.getInstance().getSoundManager().play(entitySound);
    }

    /**
     * Plays a record stack for an entity with a boombox.
     *
     * @param entityId The id of the entity to play the record at
     * @param record   The record to play
     */
    public static void playBoombox(int entityId, ItemStack record) {
        setEntitySound(entityId, null);
        if (!record.isEmpty())
            playEntityRecord(record, entityId, 0, true);
    }

    /**
     * Plays the records on an album jukebox in order.
     *
     * @param url   The URL of the stream
     * @param level The level to play records in
     * @param pos   The position of the jukebox
     */
    public static void playRadio(@Nullable String url, ClientLevel level, BlockPos pos) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        Map<BlockPos, SoundInstance> playingRecords = ((LevelRendererAccessor) Minecraft.getInstance().levelRenderer).getPlayingRecords();

        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(RadioBlock.POWERED)) // Something must already be playing since it would otherwise be -1 and a change would occur
            return;

        SoundInstance soundInstance = playingRecords.get(pos);
        if (soundInstance != null) {
            if (soundInstance instanceof StopListeningSound)
                ((StopListeningSound) soundInstance).stopListening();
            soundManager.stop(soundInstance);
            playingRecords.remove(pos);
        }

        if (state.getValue(RadioBlock.POWERED))
            return;

        if (TrackData.isValidURL(url))
            playRecord(pos, StopListeningSound.create(getEtchedRecord(url, RADIO, level, pos, AudioSource.AudioFileType.BOTH), () -> Minecraft.getInstance().tell(() -> playRadio(url, level, pos))));
    }

    /**
     * Plays the records on an album jukebox in order.
     *
     * @param jukebox The jukebox to play records
     * @param level   The level to play records in
     * @param pos     The position of the jukebox
     * @param force   Whether to force the jukebox to play
     */
    public static void playAlbum(AlbumJukeboxBlockEntity jukebox, ClientLevel level, BlockPos pos, boolean force) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        Map<BlockPos, SoundInstance> playingRecords = ((LevelRendererAccessor) Minecraft.getInstance().levelRenderer).getPlayingRecords();

        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(AlbumJukeboxBlock.POWERED) || !state.getValue(AlbumJukeboxBlock.POWERED) && !force && !jukebox.recalculatePlayingIndex(false)) // Something must already be playing since it would otherwise be -1 and a change would occur
            return;

        SoundInstance soundInstance = playingRecords.get(pos);
        if (soundInstance != null) {
            if (soundInstance instanceof StopListeningSound)
                ((StopListeningSound) soundInstance).stopListening();
            soundManager.stop(soundInstance);
            playingRecords.remove(pos);
        }

        if (level.getBlockState(pos).getValue(AlbumJukeboxBlock.POWERED))
            jukebox.stopPlaying();

        if (jukebox.getPlayingIndex() < 0) // Nothing can be played inside the jukebox
            return;

        ItemStack disc = jukebox.getItem(jukebox.getPlayingIndex());
        SoundInstance sound = null;
        if (disc.getItem() instanceof RecordItem) {
            if (level.getBlockState(pos.above()).isAir() && PlayableRecord.canShowMessage(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
                PlayableRecord.showMessage(((RecordItem) disc.getItem()).getDisplayName());
            sound = StopListeningSound.create(SimpleSoundInstance.forRecord(((RecordItem) disc.getItem()).getSound(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), () -> Minecraft.getInstance().tell(() -> playNextRecord(level, pos)));
        } else if (disc.getItem() instanceof PlayableRecord) {
            Optional<TrackData[]> optional = PlayableRecord.getStackMusic(disc);
            if (optional.isPresent()) {
                TrackData[] tracks = optional.get();
                TrackData track = jukebox.getTrack() < 0 || jukebox.getTrack() >= tracks.length ? tracks[0] : tracks[jukebox.getTrack()];
                if (TrackData.isValidURL(track.getUrl())) {
                    sound = StopListeningSound.create(getEtchedRecord(track.getUrl(), track.getDisplayName(), level, pos, AudioSource.AudioFileType.FILE), () -> Minecraft.getInstance().tell(() -> playNextRecord(level, pos)));
                }
            }
        }

        if (sound == null)
            return;

        playRecord(pos, sound);

        if (disc.getItem() instanceof RecordItem && level.getBlockState(pos).is(Blocks.JUKEBOX))
            for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(3.0D)))
                livingEntity.setRecordPlayingNearby(pos, true);
    }

    private static class DownloadTextComponent extends BaseComponent {

        private String text;
        private FormattedCharSequence visualOrderText;
        private Language decomposedWith;

        public DownloadTextComponent() {
            this.text = "";
        }

        @Override
        public String getContents() {
            return text;
        }

        @Override
        public TextComponent plainCopy() {
            return new TextComponent(this.text);
        }

        @Environment(EnvType.CLIENT)
        public FormattedCharSequence getVisualOrderText() {
            Language language = Language.getInstance();
            if (this.decomposedWith != language) {
                this.visualOrderText = language.getVisualOrder(this);
                this.decomposedWith = language;
            }

            return this.visualOrderText;
        }

        @Override
        public String toString() {
            return "TextComponent{text='" + this.text + '\'' + ", siblings=" + this.siblings + ", style=" + this.getStyle() + '}';
        }

        public void setText(String text) {
            this.text = text;
            this.decomposedWith = null;
        }
    }

    private static abstract class MusicDownloadListener implements DownloadProgressListener {

        private final Component title;
        private final DoubleSupplier x;
        private final DoubleSupplier y;
        private final DoubleSupplier z;
        private final BlockPos.MutableBlockPos pos;
        private float size;
        private Component requesting;
        private DownloadTextComponent component;

        protected MusicDownloadListener(Component title, DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
            this.title = title;
            this.x = x;
            this.y = y;
            this.z = z;
            this.pos = new BlockPos.MutableBlockPos();
        }

        private BlockPos.MutableBlockPos getPos() {
            return this.pos.set(this.x.getAsDouble(), this.y.getAsDouble(), this.z.getAsDouble());
        }

        private void setComponent(Component text) {
            if (this.component == null && (Minecraft.getInstance().level == null || !Minecraft.getInstance().level.getBlockState(this.getPos().move(Direction.UP)).isAir() || !PlayableRecord.canShowMessage(this.x.getAsDouble(), this.y.getAsDouble(), this.z.getAsDouble())))
                return;

            if (this.component == null) {
                this.component = new DownloadTextComponent();
                Minecraft.getInstance().gui.setOverlayMessage(this.component, true);
                ((GuiAccessor) Minecraft.getInstance().gui).setOverlayMessageTime(Short.MAX_VALUE);
            }
            this.component.setText(text.getString());
        }

        protected void clearComponent() {
            if (((GuiAccessor) Minecraft.getInstance().gui).getOverlayMessageString() == this.component) {
                ((GuiAccessor) Minecraft.getInstance().gui).setOverlayMessageTime(60);
                this.component = null;
            }
        }

        @Override
        public void progressStartRequest(Component component) {
            this.requesting = component;
            this.setComponent(component);
        }

        @Override
        public void progressStartDownload(float size) {
            this.size = size;
            this.requesting = null;
            this.progressStagePercentage(0);
        }

        @Override
        public void progressStagePercentage(int percentage) {
            if (this.requesting != null) {
                this.setComponent(this.requesting.copy().append(" " + percentage + "%"));
            } else if (this.size != 0) {
                this.setComponent(new TranslatableComponent("record." + Etched.MOD_ID + ".downloadProgress", String.format(Locale.ROOT, "%.2f", percentage / 100.0F * this.size), String.format(Locale.ROOT, "%.2f", this.size), this.title));
            }
        }

        @Override
        public void progressStartLoading() {
            this.requesting = null;
            this.setComponent(new TranslatableComponent("record." + Etched.MOD_ID + ".loading", this.title));
        }

        @Override
        public void onFail() {
            Minecraft.getInstance().gui.setOverlayMessage(new TranslatableComponent("record." + Etched.MOD_ID + ".downloadFail", this.title), true);
        }
    }
}
