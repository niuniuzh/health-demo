import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(HealthPlugin)
public class HealthPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "HealthPlugin"
    public let jsName = "Health"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "echo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isAvailable", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestAuthorization", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkAuthorizationStatus", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openSettings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "readSamples", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "writeSamples", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startMonitoring", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopMonitoring", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = Health()

    @objc func echo(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve([
            "value": implementation.echo(value)
        ])
    }

    @objc func isAvailable(_ call: CAPPluginCall) {
        call.resolve([
            "available": implementation.isAvailable(),
            "platform": "ios"
        ])
    }

    @objc func requestAuthorization(_ call: CAPPluginCall) {
        let readTypes = call.getArray("read", String.self) ?? []
        let writeTypes = call.getArray("write", String.self) ?? []

        implementation.requestAuthorization(readTypes: readTypes, writeTypes: writeTypes) { success, error in
            if let error = error {
                call.reject(error.localizedDescription)
            } else {
                let status = self.implementation.checkAuthorizationStatus(readTypes: readTypes, writeTypes: writeTypes)
                call.resolve(status)
            }
        }
    }

    @objc func checkAuthorizationStatus(_ call: CAPPluginCall) {
        // Since we don't have the original options here, we might need to check all supported types or wait for types to be passed
        // For simplicity, let's assume types are passed or just check all common ones.
        // Actually, the definition says it returns PermissionStatusResult, which contains read/write maps.
        // The frontend should pass the types it's interested in.
        let readTypes = call.getArray("read", String.self) ?? []
        let writeTypes = call.getArray("write", String.self) ?? []
        
        let status = implementation.checkAuthorizationStatus(readTypes: readTypes, writeTypes: writeTypes)
        call.resolve(status)
    }

    @objc func openSettings(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let url = URL(string: "x-apple-health://") else {
                call.reject("Cannot create Health URL")
                return
            }
            if UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url, options: [:]) { success in
                    call.resolve(["opened": success])
                }
            } else {
                call.reject("Cannot open Health app")
            }
        }
    }

    @objc func readSamples(_ call: CAPPluginCall) {
        let types = call.getArray("types", String.self) ?? []
        let startDateStr = call.getString("startDate") ?? ""
        let endDateStr = call.getString("endDate") ?? ""
        let limit = call.getInt("limit") ?? 0
        let ascending = call.getBool("ascending") ?? false

        let formatter = ISO8601DateFormatter()
        guard let startDate = formatter.date(from: startDateStr),
              let endDate = formatter.date(from: endDateStr) else {
            call.reject("Invalid date format. Use ISO 8601.")
            return
        }

        implementation.readSamples(types: types, startDate: startDate, endDate: endDate, limit: limit, ascending: ascending) { samples, error in
            if let error = error {
                call.reject(error.localizedDescription)
            } else {
                call.resolve(["samples": samples ?? []])
            }
        }
    }

    @objc func writeSamples(_ call: CAPPluginCall) {
        let samples = call.getArray("samples", JSObject.self) ?? []
        
        implementation.writeSamples(samples: samples) { success, writtenCount, error in
            if let error = error {
                call.reject(error.localizedDescription)
            } else {
                call.resolve([
                    "success": success,
                    "writtenCount": writtenCount
                ])
            }
        }
    }

    @objc func startMonitoring(_ call: CAPPluginCall) {
        let types = call.getArray("types", String.self) ?? []
        
        implementation.startMonitoring(types: types) { type, value in
            self.notifyListeners("monitoringUpdate", data: [
                "type": type,
                "value": value
            ])
        }
        call.resolve()
    }

    @objc func stopMonitoring(_ call: CAPPluginCall) {
        implementation.stopMonitoring()
        call.resolve()
    }
}
