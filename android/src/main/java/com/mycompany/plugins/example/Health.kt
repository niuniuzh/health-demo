package com.mycompany.plugins.example

import com.getcapacitor.Logger

class Health {
    fun echo(value: String?): String? {
        Logger.info("Echo", value ?: "null")
        return value
    }
}
