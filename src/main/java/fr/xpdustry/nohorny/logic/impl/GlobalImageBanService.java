package fr.xpdustry.nohorny.logic.impl;

import arc.util.Log;
import arc.util.serialization.Jval;
import fr.xpdustry.nohorny.logic.HornyLogicBuildContext;
import fr.xpdustry.nohorny.logic.HornyLogicBuildService;
import fr.xpdustry.nohorny.logic.HornyLogicBuildType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

public final class GlobalImageBanService implements HornyLogicBuildService {

  private static final MessageDigest messageDigest;
  private static final String GIB_API_ENDPOINT = "http://c-n.ddns.net:9999/bmi/check/?b64hash=";
  private static final int DEFAULT_CACHE_SIZE = 200;
  private static final int DEFAULT_TIMEOUT = 1000;
  private static final Pattern DRAW_FLUSH_PATTERN = Pattern.compile("^drawflush .*$", Pattern.MULTILINE);
  private static final Map<String, HornyLogicBuildType> CACHE = new LinkedHashMap<>() {
    @Override
    protected boolean removeEldestEntry(final @NotNull Entry<String, HornyLogicBuildType> eldest) {
      return size() > DEFAULT_CACHE_SIZE;
    }
  };

  static {
    try {
      messageDigest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Can't find SHA-256 algorithm.", e);
    }
  }

  @Override
  public @NotNull HornyLogicBuildType handle(final @NonNull HornyLogicBuildContext context) {
    if (!DRAW_FLUSH_PATTERN.matcher(context.code()).find()) {
      return HornyLogicBuildType.NOT_HORNY;
    }

    final var codeBlocks = DRAW_FLUSH_PATTERN.split(context.code(), -1);
    for (final var codeBlock : codeBlocks) {
      final var bytes = messageDigest.digest(codeBlock.getBytes(StandardCharsets.UTF_8));
      final var hash = Base64.getEncoder().encodeToString(bytes);
      final var type = CACHE.computeIfAbsent(hash, h -> {
        try {
          final var url = new URL(GIB_API_ENDPOINT + URLEncoder.encode(hash, StandardCharsets.UTF_8));
          final var con = (HttpURLConnection) url.openConnection();
          con.setConnectTimeout(DEFAULT_TIMEOUT);
          con.setRequestMethod("GET");
          con.setDoOutput(true);
          con.setRequestProperty("Content-Type", "application/json");

          if (con.getResponseCode() == 200) {
            try (
              final var stream = con.getInputStream();
              final var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
            ) {
              return Jval.read(reader).getBool("nudity", false) ? HornyLogicBuildType.NUDITY : HornyLogicBuildType.HORNY;
            }
          }
        } catch (IOException e) {
          Log.debug("An unexpected exception happened while querying the API.", e);
        }

        return HornyLogicBuildType.NOT_HORNY;
      });

      if (type != HornyLogicBuildType.NOT_HORNY) return type;
    }

    return HornyLogicBuildType.NOT_HORNY;
  }
}
