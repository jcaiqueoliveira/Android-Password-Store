package com.zeapo.pwdstore.git.config

sealed class Protocol {
    object Ssh: Protocol() {
        override fun toString() = "ssh://"
    }
    object Https: Protocol() {
        override fun toString() = "https://"
    }

    companion object {
        fun fromString(type: String?): Protocol = when(type) {
            "ssh://", null -> Ssh
            "https://" -> Https
            else -> throw IllegalArgumentException("$type is not a valid Protocol")
        }
    }
}
