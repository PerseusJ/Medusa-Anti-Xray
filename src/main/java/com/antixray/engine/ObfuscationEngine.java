package com.antixray.engine;

import com.antixray.cache.CacheEntry;
import com.antixray.cache.CacheKey;
import com.antixray.nms.NmsAdapter;
import com.antixray.util.MaterialSet;
import org.bukkit.World;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.antixray.engine.PaletteManipulator.computePackedArraySize;

public class ObfuscationEngine {

    private static final Logger LOGGER = Logger.getLogger("AntiXray");

    private final NmsAdapter adapter;
    private final BlockClassifier classifier;
    private final MaterialSet materialSet;
    private final AirExposureChecker exposureChecker;

    private final Mode1Engine mode1;
    private final Mode2Engine mode2;
    private final Mode3Engine mode3;

    private ObfuscationMode engineMode = ObfuscationMode.MODE_3;
    private int deepslateBelowY = 0;
    private int maxBlockHeight = 64;
    private long serverSalt = 0L;
    private double fakeOreChance = 0.07;

    public ObfuscationEngine(NmsAdapter adapter, MaterialSet materialSet, AirExposureChecker exposureChecker) {
        this.adapter = adapter;
        this.materialSet = materialSet;
        this.exposureChecker = exposureChecker;
        this.classifier = new BlockClassifier(materialSet, exposureChecker);

        this.mode1 = new Mode1Engine(adapter, classifier, materialSet);
        this.mode2 = new Mode2Engine(adapter, classifier, materialSet, exposureChecker);
        this.mode3 = new Mode3Engine(adapter, classifier, materialSet, exposureChecker);
    }

    public void obfuscateChunk(Object packet, World world, int chunkX, int chunkZ) {
        List<Object> sections = adapter.getChunkSections(packet);
        for (int i = 0; i < sections.size(); i++) {
            Object section = sections.get(i);
            if (section == null) continue;
            int sectionBaseY = (i - getSectionOffset(world)) * 16;
            obfuscateSection(section, world, chunkX, chunkZ, sectionBaseY);
        }
    }

    public void obfuscateSection(Object chunkSection, World world, int chunkX, int chunkZ, int sectionBaseY) {
        if (sectionBaseY > maxBlockHeight) return;
        if (adapter.getSectionNonEmptyCount(chunkSection) == 0) return;
        if (adapter.isDirectPalette(chunkSection)) return;

        try {
            switch (engineMode) {
                case MODE_1 -> mode1.obfuscateSection(chunkSection, world, chunkX, chunkZ,
                        sectionBaseY, deepslateBelowY, maxBlockHeight);
                case MODE_2 -> mode2.obfuscateSection(chunkSection, world, chunkX, chunkZ,
                        sectionBaseY, deepslateBelowY, maxBlockHeight, serverSalt, fakeOreChance);
                case MODE_3 -> mode3.obfuscateSection(chunkSection, world, chunkX, chunkZ,
                        sectionBaseY, deepslateBelowY, maxBlockHeight, serverSalt, fakeOreChance);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to obfuscate chunk section at (" + chunkX + ", " + chunkZ
                    + ") sectionY=" + sectionBaseY, e);
        }
    }

    private int getSectionOffset(World world) {
        return world.getMinHeight() >> 4;
    }

    public void setEngineMode(ObfuscationMode mode) {
        this.engineMode = mode;
    }

    public ObfuscationMode getEngineMode() {
        return engineMode;
    }

    public void setDeepslateBelowY(int deepslateBelowY) {
        this.deepslateBelowY = deepslateBelowY;
    }

    public int getDeepslateBelowY() {
        return deepslateBelowY;
    }

    public void setMaxBlockHeight(int maxBlockHeight) {
        this.maxBlockHeight = maxBlockHeight;
    }

    public int getMaxBlockHeight() {
        return maxBlockHeight;
    }

    public void setServerSalt(long serverSalt) {
        this.serverSalt = serverSalt;
    }

    public long getServerSalt() {
        return serverSalt;
    }

    public void setFakeOreChance(double fakeOreChance) {
        this.fakeOreChance = fakeOreChance;
    }

    public double getFakeOreChance() {
        return fakeOreChance;
    }

    public Mode1Engine getMode1Engine() {
        return mode1;
    }

    public Mode2Engine getMode2Engine() {
        return mode2;
    }

    public Mode3Engine getMode3Engine() {
        return mode3;
    }

    public NmsAdapter getAdapter() {
        return adapter;
    }

    public BlockClassifier getClassifier() {
        return classifier;
    }

    public MaterialSet getMaterialSet() {
        return materialSet;
    }

    public AirExposureChecker getExposureChecker() {
        return exposureChecker;
    }

    public byte[] serializeObfuscatedSections(List<Object> sections, World world, int chunkX, int chunkZ) {
        int sectionOffset = getSectionOffset(world);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(sections.size());
            for (int i = 0; i < sections.size(); i++) {
                Object section = sections.get(i);
                if (section == null) {
                    dos.writeBoolean(false);
                    continue;
                }
                dos.writeBoolean(true);
                if (adapter.isSingleValuePalette(section)) {
                    dos.writeBoolean(true);
                    dos.writeInt(adapter.getSingleValue(section));
                    dos.writeInt(adapter.getPaletteBitsPerEntry(section));
                } else {
                    dos.writeBoolean(false);
                    List<Integer> palette = adapter.getPaletteEntries(section);
                    long[] packed = adapter.getPackedIndices(section);
                    int bitsPerEntry = adapter.getPaletteBitsPerEntry(section);
                    dos.writeInt(palette.size());
                    for (int id : palette) {
                        dos.writeInt(id);
                    }
                    dos.writeInt(packed.length);
                    for (long v : packed) {
                        dos.writeLong(v);
                    }
                    dos.writeInt(bitsPerEntry);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to serialize obfuscated sections", e);
            return new byte[0];
        }
        return baos.toByteArray();
    }

    public void applySerializedObfuscation(byte[] data, List<Object> sections) {
        if (data == null || data.length == 0) return;
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            int count = dis.readInt();
            for (int i = 0; i < count && i < sections.size(); i++) {
                boolean nonNull = dis.readBoolean();
                if (!nonNull) continue;
                Object section = sections.get(i);
                if (section == null) continue;
                boolean isSingle = dis.readBoolean();
                if (isSingle) {
                    int singleValue = dis.readInt();
                    int bitsPerEntry = dis.readInt();
                    if (bitsPerEntry == 0) {
                        adapter.setPaletteEntries(section, List.of(singleValue));
                    } else {
                        List<Integer> palette = new ArrayList<>();
                        palette.add(singleValue);
                        int replacementId = singleValue;
                        palette.add(replacementId);
                        adapter.setPaletteEntries(section, palette);
                        adapter.setPackedIndices(section, new long[PaletteManipulator.computePackedArraySize(bitsPerEntry)], bitsPerEntry);
                    }
                } else {
                    int paletteSize = dis.readInt();
                    List<Integer> palette = new ArrayList<>(paletteSize);
                    for (int j = 0; j < paletteSize; j++) {
                        palette.add(dis.readInt());
                    }
                    int packedLen = dis.readInt();
                    long[] packed = new long[packedLen];
                    for (int j = 0; j < packedLen; j++) {
                        packed[j] = dis.readLong();
                    }
                    int bitsPerEntry = dis.readInt();
                    adapter.setPaletteEntries(section, palette);
                    if (bitsPerEntry > 0) {
                        adapter.setPackedIndices(section, packed, bitsPerEntry);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to apply serialized obfuscation", e);
        }
    }

    public CacheEntry obfuscateAndSerialize(Object packet, World world, int chunkX, int chunkZ) {
        List<Object> sections = adapter.getChunkSections(packet);
        int sectionOffset = getSectionOffset(world);
        int sectionCount = 0;
        for (int i = 0; i < sections.size(); i++) {
            Object section = sections.get(i);
            if (section == null) continue;
            int sectionBaseY = (i - sectionOffset) * 16;
            obfuscateSection(section, world, chunkX, chunkZ, sectionBaseY);
            sectionCount++;
        }
        byte[] data = serializeObfuscatedSections(sections, world, chunkX, chunkZ);
        return new CacheEntry(data, sectionCount);
    }

    public int getReplacementBlockStateId(World world, int y) {
        if (com.antixray.AntiXrayPlugin.getInstance() != null) {
            com.antixray.api.ObfuscationProvider provider = com.antixray.AntiXrayPlugin.getInstance().getAPI().getObfuscationProvider(world);
            if (provider != null) {
                org.bukkit.Material mat = provider.getReplacementBlock(world, y);
                if (mat != null) {
                    int id = adapter.getBlockStateId(mat);
                    if (id != -1) {
                        return id;
                    }
                }
            }
        }
        return materialSet.getReplacement(y, deepslateBelowY, world.getEnvironment().name());
    }
}
