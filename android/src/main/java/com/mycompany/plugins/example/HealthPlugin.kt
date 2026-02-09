package com.mycompany.plugins.example

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import androidx.health.connect.client.HealthConnectClient

@CapacitorPlugin(name = "Health")
class HealthPlugin : Plugin() {

    private val implementation = Health()

    @PluginMethod
    fun echo(call: PluginCall) {
        val value = call.getString("value")

        val ret = JSObject()
        ret.put("value", implementation.echo(value))
        call.resolve(ret)
    }

    @PluginMethod
    fun isAvailable(call: PluginCall) {
        val status = HealthConnectClient.getSdkStatus(context)
        val available = status == HealthConnectClient.SDK_AVAILABLE

        val ret = JSObject()
        ret.put("available", available)
        ret.put("platform", "android")
        call.resolve(ret)
    }
}
