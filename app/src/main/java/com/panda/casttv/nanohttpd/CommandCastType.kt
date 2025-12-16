package com.panda.casttv.nanohttpd

data class CommandCastType(
    val command: String = "",
    val url: String = "",
    val mimeType: String = "",
    val action: String = "",
    val position: Int = 0,
) {
    companion object {
        val actionPlay: CommandCastType
            get() = CommandCastType(
                command = "CONTROL",
                action = "PLAY",
            )

        val actionPause: CommandCastType
            get() = CommandCastType(
                command = "CONTROL",
                action = "PAUSE",
            )

        fun seekTo(position: Int): CommandCastType {
            return CommandCastType(
                command = "CONTROL",
                action = "SEEK",
                position = position
            )
        }
    }
}