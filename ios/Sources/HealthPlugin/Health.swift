import Foundation
import HealthKit

@objc public class Health: NSObject {
    private let healthStore = HKHealthStore()
    private var observerQueries: [String: HKObserverQuery] = [:]

    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }

    @objc public func isAvailable() -> Bool {
        return HKHealthStore.isHealthDataAvailable()
    }

    func getObjectType(_ type: String) -> HKObjectType? {
        switch type {
        case "steps":
            return HKObjectType.quantityType(forIdentifier: .stepCount)
        case "distance":
            return HKObjectType.quantityType(forIdentifier: .distanceWalkingRunning)
        case "calories":
            return HKObjectType.quantityType(forIdentifier: .activeEnergyBurned)
        case "heart_rate":
            return HKObjectType.quantityType(forIdentifier: .heartRate)
        case "sleep":
            return HKObjectType.categoryType(forIdentifier: .sleepAnalysis)
        default:
            return nil
        }
    }

    func getQuantityType(_ type: String) -> HKQuantityType? {
        switch type {
        case "steps":
            return HKQuantityType.quantityType(forIdentifier: .stepCount)
        case "distance":
            return HKQuantityType.quantityType(forIdentifier: .distanceWalkingRunning)
        case "calories":
            return HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned)
        case "heart_rate":
            return HKQuantityType.quantityType(forIdentifier: .heartRate)
        default:
            return nil
        }
    }

    func getUnit(_ unit: String) -> HKUnit {
        switch unit {
        case "count":
            return HKUnit.count()
        case "m":
            return HKUnit.meter()
        case "km":
            return HKUnit.meterUnit(with: .kilo)
        case "kcal":
            return HKUnit.kilocalorie()
        case "bpm":
            return HKUnit.count().unitDivided(by: HKUnit.minute())
        case "min":
            return HKUnit.minute()
        case "h":
            return HKUnit.hour()
        default:
            return HKUnit.count()
        }
    }

    func getStatusString(_ status: HKAuthorizationStatus) -> String {
        switch status {
        case .notDetermined:
            return "prompt"
        case .sharingDenied:
            return "denied"
        case .sharingAuthorized:
            return "granted"
        @unknown default:
            return "unsupported"
        }
    }

    private let ALL_TYPES = ["steps", "distance", "calories", "heart_rate", "sleep"]

    @objc public func requestAuthorization(readTypes: [String], writeTypes: [String], completion: @escaping (Bool, Error?) -> Void) {
        var hkReadTypes = Set<HKObjectType>()
        var hkWriteTypes = Set<HKSampleType>()

        let finalReadTypes = readTypes.isEmpty ? ALL_TYPES : readTypes
        let finalWriteTypes = writeTypes.isEmpty ? ALL_TYPES : writeTypes

        for type in finalReadTypes {
            if let objType = getObjectType(type) {
                hkReadTypes.insert(objType)
            }
        }

        for type in finalWriteTypes {
            if let objType = getObjectType(type) as? HKSampleType {
                hkWriteTypes.insert(objType)
            }
        }

        healthStore.requestAuthorization(toShare: hkWriteTypes, read: hkReadTypes) { success, error in
            completion(success, error)
        }
    }

    @objc public func checkAuthorizationStatus(readTypes: [String], writeTypes: [String]) -> [String: [String: String]] {
        var readStatus: [String: String] = [:]
        var writeStatus: [String: String] = [:]

        let finalReadTypes = readTypes.isEmpty ? ALL_TYPES : readTypes
        let finalWriteTypes = writeTypes.isEmpty ? ALL_TYPES : writeTypes
        
        for type in finalReadTypes {
            if let objType = getObjectType(type) {
                let status = healthStore.authorizationStatus(for: objType)
                readStatus[type] = getStatusString(status)
            }
        }

        for type in finalWriteTypes {
            if let objType = getObjectType(type) {
                let status = healthStore.authorizationStatus(for: objType)
                writeStatus[type] = getStatusString(status)
            }
        }

        return ["read": readStatus, "write": writeStatus]
    }

    @objc public func readSamples(types: [String], startDate: Date, endDate: Date, limit: Int, ascending: Bool, completion: @escaping ([[String: Any]]?, Error?) -> Void) {
        let group = DispatchGroup()
        var allSamples: [[String: Any]] = []
        var lastError: Error?

        for type in types {
            guard let sampleType = getObjectType(type) as? HKSampleType else { continue }
            
            group.enter()
            let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: .strictStartDate)
            let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: ascending)
            
            let query = HKSampleQuery(sampleType: sampleType, predicate: predicate, limit: limit == 0 ? HKObjectQueryNoLimit : limit, sortDescriptors: [sortDescriptor]) { _, samples, error in
                if let error = error {
                    lastError = error
                } else if let samples = samples {
                    for sample in samples {
                        var sampleData: [String: Any] = [
                            "type": type,
                            "startDate": ISO8601DateFormatter().string(from: sample.startDate),
                            "endDate": ISO8601DateFormatter().string(from: sample.endDate),
                            "sourceName": sample.sourceRevision.source.name,
                            "sourceId": sample.sourceRevision.source.bundleIdentifier
                        ]

                        if let quantitySample = sample as? HKQuantitySample {
                            let unit = self.getDefaultUnit(for: type)
                            sampleData["value"] = quantitySample.quantity.doubleValue(for: unit)
                            sampleData["unit"] = self.getUnitString(unit)
                        } else if let categorySample = sample as? HKCategorySample {
                            sampleData["value"] = Double(categorySample.value)
                            sampleData["unit"] = "count"
                        }

                        if let metadata = sample.metadata {
                            sampleData["metadata"] = metadata
                        }
                        
                        allSamples.append(sampleData)
                    }
                }
                group.leave()
            }
            healthStore.execute(query)
        }

        group.notify(queue: .main) {
            completion(allSamples, lastError)
        }
    }

    @objc public func writeSamples(samples: [[String: Any]], completion: @escaping (Bool, Int, Error?) -> Void) {
        var hkSamples: [HKSample] = []
        
        for sampleData in samples {
            guard let type = sampleData["type"] as? String,
                  let value = sampleData["value"] as? Double,
                  let startDateStr = sampleData["startDate"] as? String,
                  let startDate = ISO8601DateFormatter().date(from: startDateStr) else {
                continue
            }
            
            let endDateStr = sampleData["endDate"] as? String
            let endDate = endDateStr != nil ? ISO8601DateFormatter().date(from: endDateStr!) : startDate
            
            if let quantityType = getQuantityType(type) {
                let unit = sampleData["unit"] as? String != nil ? getUnit(sampleData["unit"] as! String) : getDefaultUnit(for: type)
                let quantity = HKQuantity(unit: unit, doubleValue: value)
                let sample = HKQuantitySample(type: quantityType, quantity: quantity, start: startDate, end: endDate ?? startDate)
                hkSamples.append(sample)
            } else if let categoryType = getObjectType(type) as? HKCategoryType {
                let sample = HKCategorySample(type: categoryType, value: Int(value), start: startDate, end: endDate ?? startDate)
                hkSamples.append(sample)
            }
        }

        if hkSamples.isEmpty {
            completion(true, 0, nil)
            return
        }

        healthStore.save(hkSamples) { success, error in
            completion(success, hkSamples.count, error)
        }
    }

    @objc public func startMonitoring(types: [String], onUpdate: @escaping (String, Double) -> Void) {
        for type in types {
            guard let sampleType = getObjectType(type) as? HKSampleType else { continue }
            
            let query = HKObserverQuery(sampleType: sampleType, predicate: nil) { _, completionHandler, error in
                if error == nil {
                    // When a change is observed, we might want to fetch the latest value.
                    // For simplicity, we can just notify that a change occurred.
                    // Or we can fetch the most recent sample.
                    self.fetchLatestSample(for: type) { value in
                        onUpdate(type, value ?? 0)
                    }
                }
                completionHandler()
            }
            
            healthStore.execute(query)
            healthStore.enableBackgroundDelivery(for: sampleType, frequency: .immediate) { _, _ in }
            observerQueries[type] = query
        }
    }

    @objc public func stopMonitoring() {
        for query in observerQueries.values {
            healthStore.stop(query)
        }
        observerQueries.removeAll()
    }

    private func fetchLatestSample(for type: String, completion: @escaping (Double?) -> Void) {
        guard let sampleType = getObjectType(type) as? HKSampleType else {
            completion(nil)
            return
        }

        let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)
        let query = HKSampleQuery(sampleType: sampleType, predicate: nil, limit: 1, sortDescriptors: [sortDescriptor]) { _, samples, _ in
            if let sample = samples?.first {
                if let quantitySample = sample as? HKQuantitySample {
                    completion(quantitySample.quantity.doubleValue(for: self.getDefaultUnit(for: type)))
                } else if let categorySample = sample as? HKCategorySample {
                    completion(Double(categorySample.value))
                } else {
                    completion(nil)
                }
            } else {
                completion(nil)
            }
        }
        healthStore.execute(query)
    }

    private func getDefaultUnit(for type: String) -> HKUnit {
        switch type {
        case "steps":
            return HKUnit.count()
        case "distance":
            return HKUnit.meter()
        case "calories":
            return HKUnit.kilocalorie()
        case "heart_rate":
            return HKUnit.count().unitDivided(by: HKUnit.minute())
        default:
            return HKUnit.count()
        }
    }

    private func getUnitString(_ unit: HKUnit) -> String {
        if unit == HKUnit.count() { return "count" }
        if unit == HKUnit.meter() { return "m" }
        if unit == HKUnit.meterUnit(with: .kilo) { return "km" }
        if unit == HKUnit.kilocalorie() { return "kcal" }
        if unit.description == "count/min" { return "bpm" }
        if unit == HKUnit.minute() { return "min" }
        if unit == HKUnit.hour() { return "h" }
        return "count"
    }
}
