/*
 * Copyright (C) 2021 - 2022 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limboapi.protocol.packet.world;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import java.util.BitSet;
import java.util.zip.Deflater;
import net.elytrium.limboapi.LimboAPI;
import net.elytrium.limboapi.api.chunk.VirtualBlock;
import net.elytrium.limboapi.api.chunk.data.ChunkSnapshot;
import net.elytrium.limboapi.api.chunk.data.LightSection;
import net.elytrium.limboapi.api.chunk.util.CompactStorage;
import net.elytrium.limboapi.api.material.Block;
import net.elytrium.limboapi.api.protocol.packets.data.BiomeData;
import net.elytrium.limboapi.material.Biome;
import net.elytrium.limboapi.mcprotocollib.BitStorage116;
import net.elytrium.limboapi.mcprotocollib.BitStorage19;
import net.elytrium.limboapi.protocol.util.NetworkSection;
import net.kyori.adventure.nbt.CompoundBinaryTag;

public class ChunkData implements MinecraftPacket {

  private final ChunkSnapshot chunk;
  private final NetworkSection[] sections;
  private final int mask;
  private final int maxSections;
  private final int nonNullSections;
  private final BiomeData biomeData;
  private final CompoundBinaryTag heightmap114;
  private final CompoundBinaryTag heightmap116;

  public ChunkData(ChunkSnapshot chunkSnapshot, boolean skyLight, int maxSections) {
    this.maxSections = maxSections;
    this.sections = new NetworkSection[maxSections];

    this.chunk = chunkSnapshot;
    int mask = 0;
    int nonNullSections = 0;
    for (int i = 0; i < this.chunk.getSections().length; ++i) {
      if (this.chunk.getSections()[i] != null) {
        ++nonNullSections;
        mask |= 1 << i;
        LightSection light = this.chunk.getLight()[i];
        NetworkSection section = new NetworkSection(
            i,
            this.chunk.getSections()[i],
            light.getBlockLight(),
            skyLight ? light.getSkyLight() : null,
            this.chunk.getBiomes()
        );
        this.sections[i] = section;
      }
    }

    this.nonNullSections = nonNullSections;
    this.mask = mask;
    this.heightmap114 = this.createHeightMap(true);
    this.heightmap116 = this.createHeightMap(false);
    this.biomeData = new BiomeData(this.chunk);
  }

  public ChunkData() {
    throw new IllegalStateException();
  }

  @Override
  public void decode(ByteBuf buf, Direction direction, ProtocolVersion protocolVersion) {

  }

  @Override
  public void encode(ByteBuf buf, Direction direction, ProtocolVersion version) {
    if (!this.chunk.isFullChunk()) {
      // 1.17 supports only full chunks.
      Preconditions.checkState(version.compareTo(ProtocolVersion.MINECRAFT_1_17) < 0);
    }

    buf.writeInt(this.chunk.getX());
    buf.writeInt(this.chunk.getZ());
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_17) < 0) {
      buf.writeBoolean(this.chunk.isFullChunk());

      if (version.compareTo(ProtocolVersion.MINECRAFT_1_16) >= 0 && version.compareTo(ProtocolVersion.MINECRAFT_1_16_2) < 0) {
        buf.writeBoolean(true); // Ignore old data.
      }

      // Mask.
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_8) > 0) {
        ProtocolUtils.writeVarInt(buf, this.mask);
      } else {
        // OptiFine devs have over-optimized the chunk loading by breaking loading of void-chunks.
        // We are changing void-chunks length here, and OptiFine client thinks that the chunk is not void-alike.
        buf.writeShort(this.mask == 0 ? 1 : this.mask);
      }
    } else if (version.compareTo(ProtocolVersion.MINECRAFT_1_17_1) <= 0) {
      // 1.17 mask.
      long[] mask = this.create117Mask();
      ProtocolUtils.writeVarInt(buf, mask.length);
      for (long m : mask) {
        buf.writeLong(m);
      }
    }

    // 1.14+ heightMap.
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_14) >= 0) {
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_16) < 0) {
        ProtocolUtils.writeCompoundTag(buf, this.heightmap114);
      } else {
        ProtocolUtils.writeCompoundTag(buf, this.heightmap116);
      }
    }

    // 1.15 - 1.17 biomes.
    if (this.chunk.isFullChunk() && version.compareTo(ProtocolVersion.MINECRAFT_1_15) >= 0 && version.compareTo(ProtocolVersion.MINECRAFT_1_17_1) <= 0) {
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_16_2) >= 0) {
        ProtocolUtils.writeVarInt(buf, this.biomeData.getPost115Biomes().length);
        for (int b : this.biomeData.getPost115Biomes()) {
          ProtocolUtils.writeVarInt(buf, b);
        }
      } else {
        for (int b : this.biomeData.getPost115Biomes()) {
          buf.writeInt(b);
        }
      }
    }

    ByteBuf data = this.createChunkData(version);
    try {
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
        ProtocolUtils.writeVarInt(buf, data.readableBytes());
        buf.writeBytes(data);
        if (version.compareTo(ProtocolVersion.MINECRAFT_1_9_4) >= 0) {
          ProtocolUtils.writeVarInt(buf, 0); // Tile entities currently doesnt supported.
        }
        if (version.compareTo(ProtocolVersion.MINECRAFT_1_17_1) > 0) {
          long[] mask = this.create117Mask();
          buf.writeBoolean(true); // Trust edges.
          ProtocolUtils.writeVarInt(buf, mask.length); // Skylight mask.
          for (long m : mask) {
            buf.writeLong(m);
          }
          ProtocolUtils.writeVarInt(buf, mask.length); // BlockLight mask.
          for (long m : mask) {
            buf.writeLong(m);
          }
          ProtocolUtils.writeVarInt(buf, 0); // EmptySkylight mask.
          ProtocolUtils.writeVarInt(buf, 0); // EmptyBlockLight mask.
          ProtocolUtils.writeVarInt(buf, this.chunk.getLight().length);
          for (LightSection section : this.chunk.getLight()) {
            ProtocolUtils.writeByteArray(buf, section.getSkyLight().getData());
          }
          ProtocolUtils.writeVarInt(buf, this.chunk.getLight().length);
          for (LightSection section : this.chunk.getLight()) {
            ProtocolUtils.writeByteArray(buf, section.getBlockLight().getData());
          }
        }
      } else {
        this.write17(buf, data);
      }
    } finally {
      ReferenceCountUtil.release(data);
    }
  }

  private ByteBuf createChunkData(ProtocolVersion version) {
    int dataLength = 0;
    for (NetworkSection networkSection : this.sections) {
      if (networkSection != null) {
        dataLength += networkSection.getDataLength(version);
      }
    }
    if (this.chunk.isFullChunk() && version.compareTo(ProtocolVersion.MINECRAFT_1_15) < 0) {
      dataLength += (version.compareTo(ProtocolVersion.MINECRAFT_1_13) < 0 ? 256 : 256 * 4);
    }
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_18) >= 0) {
      dataLength += (this.maxSections - this.nonNullSections) * 8;
    }

    ByteBuf data = Unpooled.buffer(dataLength);
    for (int pass = 0; pass < 4; ++pass) {
      for (NetworkSection section : this.sections) {
        if (section != null) {
          section.writeData(data, pass, version);
        } else if (pass == 0 && version.compareTo(ProtocolVersion.MINECRAFT_1_18) >= 0) {
          data.writeShort(0); // Block count = 0.
          data.writeByte(0); // BlockStorage: 0 bit per entry = Single palette.
          ProtocolUtils.writeVarInt(data, Block.AIR.getId()); // Only air block in the palette.
          ProtocolUtils.writeVarInt(data, 0); // BlockStorage: 0 entries.
          data.writeByte(0); // BiomeStorage: 0 bit per entry = Single palette.
          ProtocolUtils.writeVarInt(data, Biome.PLAINS.getId()); // Only Plain biome in the palette.
          ProtocolUtils.writeVarInt(data, 0); // BiomeStorage: 0 entries.
        }
      }
    }
    if (this.chunk.isFullChunk() && version.compareTo(ProtocolVersion.MINECRAFT_1_15) < 0) {
      for (byte b : this.biomeData.getPre115Biomes()) {
        if (version.compareTo(ProtocolVersion.MINECRAFT_1_13) < 0) {
          data.writeByte(b);
        } else {
          data.writeInt(b);
        }
      }
    }

    if (dataLength != data.readableBytes()) {
      LimboAPI.getLogger().warn("Data length mismatch: " + dataLength + " != " + data.readableBytes() + ". Version: " + version);
    }

    return data;
  }

  private CompoundBinaryTag createHeightMap(boolean pre116) {
    CompactStorage surface = pre116 ? new BitStorage19(9, 256) : new BitStorage116(9, 256);
    CompactStorage motionBlocking = pre116 ? new BitStorage19(9, 256) : new BitStorage116(9, 256);

    for (int y = 0; y < 256; ++y) {
      for (int x = 0; x < 16; ++x) {
        for (int z = 0; z < 16; ++z) {
          VirtualBlock block = this.chunk.getBlock(x, y, z);
          if (!block.isAir()) {
            surface.set(x + (z << 4), y + 1);
          }
          if (block.isMotionBlocking()) {
            motionBlocking.set(x + (z << 4), y + 1);
          }
        }
      }
    }

    return CompoundBinaryTag.builder()
        .putLongArray("MOTION_BLOCKING", motionBlocking.getData())
        .putLongArray("WORLD_SURFACE", surface.getData())
        .build();
  }

  private long[] create117Mask() {
    return BitSet.valueOf(
        new long[] {
            this.mask
        }
    ).toLongArray();
  }

  private void write17(ByteBuf out, ByteBuf data) {
    out.writeShort(0); // Extended bitmask.
    byte[] uncompressed = new byte[data.readableBytes()];
    data.readBytes(uncompressed);
    ByteBuf compressed = Unpooled.buffer();
    Deflater deflater = new Deflater(9);
    try {
      deflater.setInput(uncompressed);
      deflater.finish();
      byte[] buffer = new byte[1024];
      while (!deflater.finished()) {
        int count = deflater.deflate(buffer);
        compressed.writeBytes(buffer, 0, count);
      }
      out.writeInt(compressed.readableBytes()); // Compressed size.
      out.writeBytes(compressed);
    } finally {
      deflater.end();
      compressed.release();
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return true;
  }
}

