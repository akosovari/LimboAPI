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

package net.elytrium.limboapi.server.world.chunk;

import com.google.common.base.Preconditions;
import net.elytrium.limboapi.api.chunk.data.LightSection;
import net.elytrium.limboapi.api.mcprotocollib.NibbleArray3d;

public class SimpleLightSection implements LightSection {

  public static final NibbleArray3d NO_LIGHT = new NibbleArray3d(SimpleChunk.MAX_BLOCKS_PER_SECTION);
  public static final NibbleArray3d ALL_LIGHT = new NibbleArray3d(SimpleChunk.MAX_BLOCKS_PER_SECTION, 15);
  public static final LightSection DEFAULT = new SimpleLightSection();

  private NibbleArray3d blockLight = NO_LIGHT;
  private NibbleArray3d skyLight = ALL_LIGHT;
  private long lastUpdate = System.nanoTime();

  public SimpleLightSection() {

  }

  private SimpleLightSection(NibbleArray3d blockLight, NibbleArray3d skyLight, long lastUpdate) {
    this.blockLight = blockLight;
    this.skyLight = skyLight;
    this.lastUpdate = lastUpdate;
  }

  @Override
  public NibbleArray3d getBlockLight() {
    return this.blockLight;
  }

  @Override
  public byte getBlockLight(int x, int y, int z) {
    this.checkIndexes(x, y, z);
    return (byte) this.blockLight.get(x, y, z);
  }

  @Override
  public NibbleArray3d getSkyLight() {
    return this.skyLight;
  }

  @Override
  public byte getSkyLight(int x, int y, int z) {
    this.checkIndexes(x, y, z);
    return (byte) this.skyLight.get(x, y, z);
  }

  @Override
  public void setBlockLight(int x, int y, int z, byte light) {
    this.checkIndexes(x, y, z);
    Preconditions.checkArgument(light >= 0 && light <= 15, "light should be between 0 and 15");

    if (this.blockLight == NO_LIGHT && light != 0) {
      this.blockLight = new NibbleArray3d(SimpleChunk.MAX_BLOCKS_PER_SECTION);
    }

    this.blockLight.set(x, y, z, light);
    this.lastUpdate = System.nanoTime();
  }

  @Override
  public SimpleLightSection copy() {
    NibbleArray3d skyLight = this.skyLight == ALL_LIGHT ? ALL_LIGHT : this.skyLight.copy();
    NibbleArray3d blockLight = this.blockLight == NO_LIGHT ? NO_LIGHT : this.blockLight.copy();
    return new SimpleLightSection(blockLight, skyLight, this.lastUpdate);
  }

  @Override
  public long getLastUpdate() {
    return this.lastUpdate;
  }

  @Override
  public void setSkyLight(int x, int y, int z, byte light) {
    this.checkIndexes(x, y, z);
    Preconditions.checkArgument(light >= 0 && light <= 15, "light should be between 0 and 15");
    if (this.skyLight == ALL_LIGHT && light != 15) {
      this.skyLight = new NibbleArray3d(SimpleChunk.MAX_BLOCKS_PER_SECTION);
    }
    this.skyLight.set(x, y, z, light);
    this.lastUpdate = System.nanoTime();
  }

  private void checkIndexes(int x, int y, int z) {
    Preconditions.checkArgument(this.checkIndex(x), "x should be between 0 and 15");
    Preconditions.checkArgument(this.checkIndex(y), "y should be between 0 and 15");
    Preconditions.checkArgument(this.checkIndex(z), "z should be between 0 and 15");
  }

  private boolean checkIndex(int i) {
    return i >= 0 && i <= 15;
  }
}
