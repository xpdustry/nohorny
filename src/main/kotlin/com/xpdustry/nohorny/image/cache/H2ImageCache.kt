/*
 * This file is part of NoHorny. The plugin securing your server against nsfw builds.
 *
 * MIT License
 *
 * Copyright (c) 2024 Xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xpdustry.nohorny.image.cache

import arc.struct.IntSeq
import com.xpdustry.nohorny.NoHornyListener
import com.xpdustry.nohorny.extension.resize
import com.xpdustry.nohorny.geometry.IndexGroup
import com.xpdustry.nohorny.image.NoHornyImage
import com.xpdustry.nohorny.image.NoHornyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.sql.Connection
import java.util.BitSet
import java.util.concurrent.CompletableFuture
import javax.sql.DataSource
import kotlin.time.Duration.Companion.minutes

internal class H2ImageCache(
    private val datasource: DataSource,
    private val config: ImageCacheConfig.Local,
) : ImageCache, NoHornyListener("Database", Dispatchers.IO) {
    override fun onInit() {
        datasource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS `nh_image` (
                        `id`         INTEGER     NOT NULL AUTO_INCREMENT,
                        `rating`     VARCHAR(32) NOT NULL,
                        `last_match` TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (`id`) 
                    );
                    """.trimIndent(),
                )

                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS `nh_image_hash` (
                        `image_id` INTEGER         NOT NULL,
                        `hash`     VARBINARY(1024) NOT NULL,
                        PRIMARY KEY (`image_id`, `hash`),
                        FOREIGN KEY (`image_id`) REFERENCES `nh_image`(`id`) ON DELETE CASCADE
                    );
                    """.trimIndent(),
                )

                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS `nh_image_meta` (
                        `image_id` INTEGER     NOT NULL,
                        `name`     VARCHAR(32) NOT NULL,
                        `value`    FLOAT       NOT NULL,
                        PRIMARY KEY (`image_id`, `name`),
                        FOREIGN KEY (`image_id`) REFERENCES `nh_image`(`id`) ON DELETE CASCADE
                    );
                    """.trimIndent(),
                )
            }
        }

        scope.launch {
            while (isActive) {
                cleanup()
                delay(10.minutes)
            }
        }
    }

    override fun getResult(
        group: IndexGroup<out NoHornyImage>,
        image: BufferedImage,
    ): CompletableFuture<NoHornyResult?> =
        scope.future {
            val hashes = computeBlockMeanHashRedundant(image)
            if (hashes.isEmpty()) return@future null
            datasource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        CREATE LOCAL TEMPORARY TABLE IF NOT EXISTS `nh_input` (
                            `hash` VARBINARY(1024) NOT NULL,
                            PRIMARY KEY (`hash`)
                        )
                        """.trimIndent(),
                    )
                }

                @Suppress("SqlWithoutWhere")
                connection.createStatement().use { statement ->
                    statement.execute("DELETE FROM `nh_input`")
                }

                connection.prepareStatement("INSERT INTO `nh_input` (`hash`) VALUES (?)").use { statement ->
                    for (hash in hashes) {
                        statement.setBytes(1, hash.array())
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }

                val matched = IntSeq()
                var info = NoHornyResult.EMPTY
                connection.prepareStatement(
                    """
                    SELECT 
                        i.id AS `image_id`, 
                        SUM(ROUND(100.0 * (CASE WHEN (h.`hash` in (SELECT `hash` FROM `nh_input`)) THEN 1 ELSE 0 END) / (SELECT count(*) FROM `nh_input`), 2)) as `match_percent`
                    FROM 
                        `nh_image`      i
                    JOIN 
                        `nh_image_hash` h ON i.`id` = h.`image_id`
                    WHERE 
                        h.`hash` in (SELECT `hash` FROM `nh_input`)
                    GROUP BY 
                        `image_id`
                    HAVING 
                        `match_percent` > 70
                    ORDER BY
                        `match_percent` DESC;
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        while (result.next()) {
                            val identifier = result.getInt("image_id")
                            matched.add(identifier)
                            val temp = getStoredResult(connection, identifier)
                            if (temp.rating > info.rating || info == NoHornyResult.EMPTY) info = temp
                        }
                    }
                }

                repeat(matched.size) {
                    val identifier = matched.get(it)
                    connection.prepareStatement(
                        """
                        UPDATE 
                            `nh_image`
                        SET 
                            `last_match` = CURRENT_TIMESTAMP
                        WHERE 
                            `id` = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setInt(1, identifier)
                        statement.executeUpdate()
                    }
                }

                info.takeUnless { it == NoHornyResult.EMPTY }
            }
        }

    override fun putResult(
        group: IndexGroup<out NoHornyImage>,
        image: BufferedImage,
        result: NoHornyResult,
    ) {
        scope.launch {
            val hashes = computeBlockMeanHashRedundant(image)
            if (hashes.isEmpty()) return@launch
            datasource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO 
                        `nh_image` (`rating`)
                    VALUES 
                        (?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, result.rating.name)
                    statement.executeUpdate()
                }

                val identifier =
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT LAST_INSERT_ID()").use { result ->
                            result.next()
                            result.getInt(1)
                        }
                    }

                connection.prepareStatement(
                    """
                    INSERT INTO 
                        `nh_image_hash` (`image_id`, `hash`)
                    VALUES 
                        (?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    for (hash in hashes) {
                        statement.setInt(1, identifier)
                        statement.setBytes(2, hash.array())
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }

                connection.prepareStatement(
                    """
                    INSERT INTO 
                        `nh_image_meta` (`image_id`, `name`, `value`)
                    VALUES 
                        (?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    for ((kind, value) in result.details) {
                        statement.setInt(1, identifier)
                        statement.setString(2, kind.name)
                        statement.setFloat(3, value)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
            }
        }
    }

    private fun getStoredResult(
        connection: Connection,
        id: Int,
    ): NoHornyResult {
        val rating =
            connection.prepareStatement(
                """
                SELECT 
                    `rating`
                FROM 
                    `nh_image`
                WHERE 
                    `id` = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, id)
                statement.executeQuery().use { result ->
                    if (!result.next()) return@getStoredResult NoHornyResult.EMPTY
                    try {
                        NoHornyResult.Rating.valueOf(result.getString("rating"))
                    } catch (e: IllegalArgumentException) {
                        return@getStoredResult NoHornyResult.EMPTY
                    }
                }
            }

        val details = mutableMapOf<NoHornyResult.Kind, Float>()
        connection.prepareStatement(
            """
            SELECT 
                `name`, `value`
            FROM 
                `nh_image_meta`
            WHERE 
                `image_id` = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, id)
            statement.executeQuery().use { result ->
                while (result.next()) {
                    val kind =
                        try {
                            NoHornyResult.Kind.valueOf(result.getString("name"))
                        } catch (e: IllegalArgumentException) {
                            continue
                        }
                    details[kind] = result.getFloat("value")
                }
            }
        }

        return NoHornyResult(rating, details)
    }

    private fun cleanup() =
        datasource.connection.use { connection ->
            connection.prepareStatement(
                """
                DELETE FROM 
                    `nh_image` n1
                WHERE 
                    DATEDIFF('MINUTE', n1.`last_match`, CURRENT_TIMESTAMP()) >= ?
                    OR 
                    n1.`id` NOT IN (
                        SELECT 
                            n2.`id`
                        FROM 
                            `nh_image` n2
                        ORDER BY 
                            `last_match` DESC
                        LIMIT ?
                    )
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, config.retention.inWholeMinutes)
                statement.setInt(2, config.maxSize)
                val count = statement.executeUpdate()
                logger.debug("Cleanup, deleted {} expired images", count)
            }
        }

    private fun computeBlockMeanHashRedundant(image: BufferedImage): Set<ByteBuffer> {
        val hashes = mutableSetOf<ByteBuffer>()

        var resized = image
        val dw = image.width % BLOCK_SIZE
        val dh = image.height % BLOCK_SIZE
        if (dw != 0 || dh != 0) {
            resized = image.resize(image.width + (BLOCK_SIZE - dw), image.height + (BLOCK_SIZE - dh))
        }

        repeat(resized.height / BLOCK_SIZE) { y ->
            repeat(resized.width / BLOCK_SIZE) { x ->
                val hash = computeBlockMeanHash(resized.getSubimage(x * BLOCK_SIZE, y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE))
                if (hash != null) {
                    hashes += ByteBuffer.wrap(hash.toByteArray())
                }
            }
        }

        return hashes
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun computeBlockMeanHash(block: BufferedImage): BitSet? {
        val color = block.getRGB(0, 0)
        val buffer = UByteArray(block.width * block.height)
        var sum = 0
        var solid = true
        for (y in 0 until block.height) {
            for (x in 0 until block.width) {
                val rgb = block.getRGB(x, y)
                if (rgb != color) solid = false
                val r = rgb shr 16 and 0xFF
                val g = rgb shr 8 and 0xFF
                val b = rgb and 0xFF
                val gray = (r + g + b) / 3
                sum += gray
                buffer[(y * block.width) + x] = gray.toUByte()
            }
        }
        if (solid) return null
        val mean = sum / (block.height * block.width)
        val bits = BitSet(block.height * block.width)
        for (i in buffer.indices) bits[i] = buffer[i] > mean.toUByte()
        return bits
    }

    companion object {
        private const val BLOCK_SIZE = 32
        private val logger = LoggerFactory.getLogger(H2ImageCache::class.java)
    }
}
