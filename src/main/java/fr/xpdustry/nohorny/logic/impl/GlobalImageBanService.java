package fr.xpdustry.nohorny.logic.impl;

import arc.util.Log;
import fr.xpdustry.nohorny.logic.HornyLogicBuildContext;
import fr.xpdustry.nohorny.logic.HornyLogicBuildService;
import fr.xpdustry.nohorny.logic.HornyLogicBuildType;
import java.io.IOException;
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

/**
 * Default implementation of the {@link HornyLogicBuildService} backed by GIB, a verification server hosted by Chaotic-Neutral.
 * It is pretty efficient but quite slow due to individual http requests for each code block.
 */
public final class GlobalImageBanService implements HornyLogicBuildService {

  private static final String GIB_API_ENDPOINT = "http://c-n.ddns.net:9999/bmi/check/?b64hash=";
  private static final int DEFAULT_TIMEOUT = 1000;
  private static final Pattern DRAW_FLUSH_PATTERN = Pattern.compile("^drawflush .*$", Pattern.MULTILINE);
  // Thread local because it #handle can be called my other threads from the ServicePipeline
  private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST_INSTANCE = ThreadLocal.withInitial(() -> {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Can't find SHA-256 algorithm.", e);
    }
  });

  private final boolean deepSearch;
  private final int cacheSize;
  private final Map<String, HornyLogicBuildType> cache;

  public GlobalImageBanService(final boolean deepSearch, final int cacheSize) {
    this.deepSearch = deepSearch;
    this.cacheSize = cacheSize;
    this.cache = new LinkedHashMap<>(cacheSize) {
      @Override
      protected boolean removeEldestEntry(final @NotNull Entry<String, HornyLogicBuildType> eldest) {
        return size() + 1 > GlobalImageBanService.this.cacheSize;
      }
    };
  }

  @Override
  public @NotNull HornyLogicBuildType handle(final @NonNull HornyLogicBuildContext context) {
    if (!DRAW_FLUSH_PATTERN.matcher(context.code()).find()) {
      return HornyLogicBuildType.NOT_HORNY;
    }

    final var codeBlocks = deepSearch
      ? DRAW_FLUSH_PATTERN.split(context.code(), -1)
      : new String[] {context.code()};

    for (final var codeBlock : codeBlocks) {
      final var bytes = MESSAGE_DIGEST_INSTANCE.get().digest(codeBlock.getBytes(StandardCharsets.UTF_8));
      final var hash = Base64.getEncoder().encodeToString(bytes);
      final var type = cache.computeIfAbsent(hash, this::queryGibDatabase);
      if (type == HornyLogicBuildType.HORNY) {
        Log.debug("GIB: Hit @ at (@, @)", context.player().name(), context.tile().x, context.tile().y);
        return type;
      }
    }

    Log.debug("GIB: Miss @ at (@, @)", context.player().name(), context.tile().x, context.tile().y);
    return HornyLogicBuildType.NOT_HORNY;
  }

  private @NotNull HornyLogicBuildType queryGibDatabase(final @NotNull String hash) {
    try {
      Log.debug("GIB: Querying hash @", hash);

      final var url = new URL(GIB_API_ENDPOINT + URLEncoder.encode(hash, StandardCharsets.UTF_8));
      final var con = (HttpURLConnection) url.openConnection();
      con.setConnectTimeout(DEFAULT_TIMEOUT);
      con.setRequestMethod("GET");
      con.setDoOutput(true);
      con.setRequestProperty("Content-Type", "application/json");

      if (con.getResponseCode() == 200) return HornyLogicBuildType.HORNY;
    } catch (IOException e) {
      Log.err("An unexpected exception happened while querying the GIB API.", e);
    }

    return HornyLogicBuildType.NOT_HORNY;
  }
}
