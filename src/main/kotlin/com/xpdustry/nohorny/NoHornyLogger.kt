/*
 * This file is part of NoHorny. The plugin securing your server against nsfw.
 *
 * MIT License
 *
 * Copyright (c) 2023 Xpdustry
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
package com.xpdustry.nohorny

import arc.util.Log
import mindustry.Vars
import org.slf4j.LoggerFactory

internal interface NoHornyLogger {

    fun error(text: String, vararg args: Any = emptyArray())

    fun info(text: String, vararg args: Any = emptyArray())

    fun debug(text: String, vararg args: Any = emptyArray())

    fun trace(text: String, vararg args: Any = emptyArray())

    companion object : NoHornyLogger by findImplementation()

    object ARC : NoHornyLogger {

        override fun error(text: String, vararg args: Any) = log(Log.LogLevel.err, text, args)

        override fun info(text: String, vararg args: Any) = log(Log.LogLevel.info, text, args)

        override fun debug(text: String, vararg args: Any) = log(Log.LogLevel.debug, text, args)

        override fun trace(text: String, vararg args: Any) = log(Log.LogLevel.debug, text, args)

        private fun log(level: Log.LogLevel, text: String, args: Array<out Any>) {
            val throwable: Throwable?
            val arguments: Array<out Any>
            if (args.lastOrNull() is Throwable) {
                throwable = args.last() as Throwable
                arguments = args.copyOfRange(0, args.size - 1)
            } else {
                throwable = null
                arguments = args
            }
            Log.log(level, "[NoHornyPlugin] ${text.replace("{}", "@")}", *arguments)
            if (throwable != null) {
                Log.log(level, "[NoHornyPlugin] ${throwable.stackTraceToString()}")
            }
        }
    }

    object SLF4J : NoHornyLogger {

        private val logger = LoggerFactory.getLogger(NoHornyPlugin::class.java)

        override fun error(text: String, vararg args: Any) = logger.error(text, *args)

        override fun info(text: String, vararg args: Any) = logger.info(text, *args)

        override fun debug(text: String, vararg args: Any) = logger.debug(text, *args)

        override fun trace(text: String, vararg args: Any) = logger.trace(text, *args)
    }
}

private fun findImplementation() =
    if (Vars.mods.getMod("distributor-core") != null ||
        Vars.mods.getMod("distributor-logging") != null)
        NoHornyLogger.SLF4J
    else NoHornyLogger.ARC
