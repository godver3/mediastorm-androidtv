package com.strmr.tv.player.dv7.mkv;

import androidx.annotation.Nullable;

public final class DvDolbyVisionConfig {
  public final int profile;
  public final int level;
  public final String codecs;

  @Nullable
  public static DvDolbyVisionConfig parse(byte[] data) {
    if (data == null || data.length < 4) {
      return null;
    }

    int profileByte = data[2] & 0xFF;
    int levelByte = data[3] & 0xFF;
    int profile = profileByte >> 1;
    int level = ((profileByte & 0x01) << 5) | ((levelByte >> 3) & 0x1F);
    String prefix;
    switch (profile) {
      case 4:
      case 5:
      case 7:
      case 8:
        prefix = "dvhe";
        break;
      case 9:
        prefix = "dvav";
        break;
      case 10:
        prefix = "dav1";
        break;
      default:
        return null;
    }

    String codecs =
        prefix
            + (profile < 10 ? ".0" : ".")
            + profile
            + (level < 10 ? ".0" : ".")
            + level;
    return new DvDolbyVisionConfig(profile, level, codecs);
  }

  private DvDolbyVisionConfig(int profile, int level, String codecs) {
    this.profile = profile;
    this.level = level;
    this.codecs = codecs;
  }
}
