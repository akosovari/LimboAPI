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

package net.elytrium.limboapi.protocol.data;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.elytrium.limboapi.api.chunk.VirtualBlock;
import net.elytrium.limboapi.api.chunk.data.BlockStorage;
import net.elytrium.limboapi.api.chunk.util.CompactStorage;
import net.elytrium.limboapi.mcprotocollib.BitStorage116;
import net.elytrium.limboapi.mcprotocollib.BitStorage19;
import net.elytrium.limboapi.server.world.SimpleBlock;
import net.elytrium.limboapi.server.world.chunk.SimpleChunk;
import org.checkerframework.checker.nullness.qual.NonNull;

public class BlockStorage19 implements BlockStorage {

  private final ProtocolVersion version;
  private List<VirtualBlock> palette = new ArrayList<>();
  private Map<Short, VirtualBlock> rawToBlock = new HashMap<>();
  private CompactStorage storage;

  public BlockStorage19(ProtocolVersion version) {
    this.version = version;
    this.palette.add(SimpleBlock.AIR);
    this.rawToBlock.put(SimpleBlock.AIR.getId(version), SimpleBlock.AIR);
    this.storage = this.createStorage(4);
  }

  private BlockStorage19(ProtocolVersion version, List<VirtualBlock> palette, Map<Short, VirtualBlock> rawToBlock, CompactStorage storage) {
    this.version = version;
    this.palette = palette;
    this.rawToBlock = rawToBlock;
    this.storage = storage;
  }

  @Override
  public void set(int x, int y, int z, @NonNull VirtualBlock block) {
    int id = this.getIndex(block);
    this.storage.set(index(x, y, z), id);
  }

  @NonNull
  @Override
  public VirtualBlock get(int x, int y, int z) {
    return this.get(index(x, y, z));
  }

  private VirtualBlock get(int index) {
    int id = this.storage.get(index);

    if (this.storage.getBitsPerEntry() > 8) {
      return this.rawToBlock.get((short) id);
    } else {
      return this.palette.get(id);
    }
  }

  @Override
  public void write(Object byteBufObject, ProtocolVersion version) {
    if (!(byteBufObject instanceof ByteBuf)) {
      throw new IllegalArgumentException("Not ByteBuf");
    }
    ByteBuf buf = (ByteBuf) byteBufObject;
    buf.writeByte(this.storage.getBitsPerEntry());
    if (this.storage.getBitsPerEntry() > 8) {
      if (this.version.compareTo(ProtocolVersion.MINECRAFT_1_13) < 0) {
        ProtocolUtils.writeVarInt(buf, 0);
      }
    } else {
      ProtocolUtils.writeVarInt(buf, this.palette.size());
      for (VirtualBlock state : this.palette) {
        ProtocolUtils.writeVarInt(buf, state.getId(this.version));
      }
    }

    this.storage.write(buf, version);
  }

  @Override
  public int getDataLength(ProtocolVersion version) {
    int length = 1;
    if (this.storage.getBitsPerEntry() > 8) {
      if (this.version.compareTo(ProtocolVersion.MINECRAFT_1_13) < 0) {
        length += 1;
      }
    } else {
      length += ProtocolUtils.varIntBytes(this.palette.size());
      for (VirtualBlock state : this.palette) {
        length += ProtocolUtils.varIntBytes(state.getId(this.version));
      }
    }

    return length + this.storage.getDataLength();
  }

  @Override
  public BlockStorage copy() {
    return new BlockStorage19(this.version, new ArrayList<>(this.palette), new HashMap<>(this.rawToBlock), this.storage.copy());
  }

  private int getIndex(VirtualBlock block) {
    if (this.storage.getBitsPerEntry() > 8) {
      short raw = block.getId(this.version);
      this.rawToBlock.put(raw, block);
      return raw;
    }
    int id = this.palette.indexOf(block);
    if (id == -1) {
      if (this.palette.size() >= (1 << this.storage.getBitsPerEntry())) {
        this.resize(this.storage.getBitsPerEntry() + 1);
        return this.getIndex(block);
      }
      this.palette.add(block);
      id = this.palette.size() - 1;
    }
    return id;
  }

  private void resize(int newSize) {
    newSize = StorageUtils19.fixBitsPerEntry(this.version, newSize);
    CompactStorage newStorage = this.createStorage(newSize);

    for (int i = 0; i < SimpleChunk.MAX_BLOCKS_PER_SECTION; ++i) {
      int newId = newSize > 8 ? this.palette.get(this.storage.get(i)).getId(this.version) : this.storage.get(i);
      newStorage.set(i, newId);
    }
    this.storage = newStorage;
  }

  private CompactStorage createStorage(int bits) {
    return this.version.compareTo(ProtocolVersion.MINECRAFT_1_16) < 0
        ? new BitStorage19(bits, SimpleChunk.MAX_BLOCKS_PER_SECTION)
        : new BitStorage116(bits, SimpleChunk.MAX_BLOCKS_PER_SECTION);
  }

  @Override
  public String toString() {
    return "BlockStorage19{"
        + "version=" + this.version
        + ", palette=" + this.palette
        + ", rawToBlock=" + this.rawToBlock
        + ", storage=" + this.storage
        + '}';
  }

  private static int index(int x, int y, int z) {
    return y << 8 | z << 4 | x;
  }
}
