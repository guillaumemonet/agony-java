package com.artandmagic.agony.data;

import com.artandmagic.agony.audio.ModPlayer;
import com.artandmagic.agony.audio.SoundFx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the original data files when they are available.
 *
 * <p>The source release of Agony contains the code but none of the data: the
 * assembler pulls the graphics, the maps and the music in with {@code INCBIN}
 * from {@code WORK:Agony/}, and those files are only on the game disks.  This
 * loader picks up whatever of them it finds in a folder next to the game and
 * leaves the rest to {@link PlaceholderAssets}.
 *
 * <p>Recognised files:
 * <ul>
 *   <li>{@code music.mod} &mdash; the level music, the same module the
 *       {@code Muzack/} sources hold, in its usual container</li>
 *   <li>{@code sound0.raw} to {@code sound3.raw} &mdash; the four effects of
 *       {@code Sounds/Sound.bin}, as raw signed eight bit samples, in the order
 *       bonus, alien destroyed, player fires, alien hit</li>
 * </ul>
 */
public final class AssetLoader {

    private final Path root;

    public AssetLoader(Path root) {
        this.root = root;
    }

    /** True when the folder exists and can be read. */
    public boolean available() {
        return root != null && Files.isDirectory(root);
    }

    /** Loads the level music, returning false when there is none to load. */
    public boolean loadMusic(ModPlayer player) {
        byte[] data = read("music.mod");
        if (data == null || !player.load(data)) {
            return false;
        }
        player.init();
        return true;
    }

    /** Installs the effects carried by the converted game data. */
    public static int installSounds(SoundFx sound, com.artandmagic.agony.data.GameAssets assets) {
        int loaded = 0;
        for (int i = 0; i < assets.soundSamples.length; i++) {
            if (assets.soundSamples[i] != null) {
                sound.setSample(i, assets.soundSamples[i], assets.soundPeriod);
                loaded++;
            }
        }
        return loaded;
    }

    /** Loads the four sound effects from loose files; missing ones stay silent. */
    public int loadSounds(SoundFx sound) {
        int loaded = 0;
        for (int i = 0; i < 4; i++) {
            byte[] data = read("sound" + i + ".raw");
            if (data != null) {
                sound.setSample(i, data, 277);
                loaded++;
            }
        }
        return loaded;
    }

    private byte[] read(String name) {
        if (!available()) {
            return null;
        }
        Path file = root.resolve(name);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            return null;
        }
    }
}
