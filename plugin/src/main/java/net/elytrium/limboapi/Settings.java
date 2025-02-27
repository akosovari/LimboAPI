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

package net.elytrium.limboapi;

import java.util.List;
import net.elytrium.java.commons.config.YamlConfig;

public class Settings extends YamlConfig {

  @Ignore
  public static final Settings IMP = new Settings();

  @Final
  public String VERSION = BuildConstants.LIMBO_VERSION;

  @Comment({
      "Available serializers:",
      "LEGACY_AMPERSAND - \"&c&lExample &c&9Text\".",
      "LEGACY_SECTION - \"§c§lExample §c§9Text\".",
      "MINIMESSAGE - \"<bold><red>Example</red> <blue>Text</blue></bold>\". (https://webui.adventure.kyori.net/)",
      "GSON - \"[{\"text\":\"Example\",\"bold\":true,\"color\":\"red\"},{\"text\":\" \",\"bold\":true},{\"text\":\"Text\",\"bold\":true,\"color\":\"blue\"}]\". (https://minecraft.tools/en/json_text.php/)",
      "GSON_COLOR_DOWNSAMPLING - Same as GSON, but uses downsampling."
  })
  public String SERIALIZER = "LEGACY_AMPERSAND";
  public String PREFIX = "LimboAPI &6>>&f";

  @Create
  public MAIN MAIN;

  @Comment("Don't use \\n, use {NL} for new line, and {PRFX} for prefix.")
  public static class MAIN {

    public boolean CHECK_FOR_UPDATES = true;

    public int MAX_CHAT_MESSAGE_LENGTH = 256;
    public int MAX_UNKNOWN_PACKET_LENGTH = 2048;
    public int MAX_SINGLE_GENERIC_PACKET_LENGTH = 3072;
    public int MAX_MULTI_GENERIC_PACKET_LENGTH = 131072;

    @Comment("Logging for connect and disconnect messages.")
    public boolean LOGGING_ENABLED = true;
    @Comment({
        "Change the parameters below, if you want to reduce the RAM consumption.",
        "If you want to completely block Minecraft versions, use VelocityTools. (https://github.com/Elytrium/VelocityTools/releases/latest)",
        "Available versions:",
        "1_7_2, 1_7_6, 1_8, 1_9, 1_9_1, 1_9_2, 1_9_4, 1_10, 1_11, 1_11_1, 1_12, 1_12_1, 1_12_2,",
        "1_13, 1_13_1, 1_13_2, 1_14, 1_14_1, 1_14_2, 1_14_3, 1_14_4, 1_15, 1_15_1, 1_15_2,",
        "1_16, 1_16_1, 1_16_2, 1_16_3, 1_16_4, 1_17, 1_17_1, 1_18, 1_18_2, LATEST"
    })
    public String PREPARE_MIN_VERSION = "1_7_2";
    public String PREPARE_MAX_VERSION = "LATEST";

    @Comment("Helpful if you want some plugins proceed before LimboAPI. For example, it is needed to Floodgate to replace UUID.")
    public List<String> PRE_LIMBO_PROFILE_REQUEST_PLUGINS = List.of("floodgate", "geyser");

    @Comment("Regenerates listeners that need to proceed before LimboAPI on each EventManager#register call.")
    public boolean AUTO_REGENERATE_LISTENERS = false;

    @Create
    public MESSAGES MESSAGES;

    public static class MESSAGES {

      public String TOO_BIG_PACKET = "{PRFX}{NL}{NL}&cYour client sent too big packet!";
      public String INVALID_PING = "{PRFX}{NL}{NL}&cYour client sent invalid ping packet!";
      public String TIME_OUT = "{PRFX}{NL}{NL}Timed out.";
    }
  }
}
