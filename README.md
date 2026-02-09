# health-demo

for health step

## Install

```bash
npm install health-demo
npx cap sync
```

## API

<docgen-index>

* [`echo(...)`](#echo)
* [`isAvailable()`](#isavailable)
* [`requestAuthorization(...)`](#requestauthorization)
* [`checkAuthorizationStatus()`](#checkauthorizationstatus)
* [`openSettings()`](#opensettings)
* [`readSamples(...)`](#readsamples)
* [`writeSamples(...)`](#writesamples)
* [`startMonitoring(...)`](#startmonitoring)
* [`stopMonitoring()`](#stopmonitoring)
* [`addListener('monitoringUpdate', ...)`](#addlistenermonitoringupdate-)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### echo(...)

```typescript
echo(options: { value: string; }) => Promise<{ value: string; }>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ value: string; }</code> |

**Returns:** <code>Promise&lt;{ value: string; }&gt;</code>

--------------------


### isAvailable()

```typescript
isAvailable() => Promise<{ available: boolean; platform: "ios" | "android" | "web"; }>
```

**Returns:** <code>Promise&lt;{ available: boolean; platform: 'ios' | 'android' | 'web'; }&gt;</code>

--------------------


### requestAuthorization(...)

```typescript
requestAuthorization(options?: PermissionRequestOptions | undefined) => Promise<PermissionStatusResult>
```

| Param         | Type                                                                          |
| ------------- | ----------------------------------------------------------------------------- |
| **`options`** | <code><a href="#permissionrequestoptions">PermissionRequestOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#permissionstatusresult">PermissionStatusResult</a>&gt;</code>

--------------------


### checkAuthorizationStatus()

```typescript
checkAuthorizationStatus() => Promise<PermissionStatusResult>
```

**Returns:** <code>Promise&lt;<a href="#permissionstatusresult">PermissionStatusResult</a>&gt;</code>

--------------------


### openSettings()

```typescript
openSettings() => Promise<{ opened: boolean; }>
```

**Returns:** <code>Promise&lt;{ opened: boolean; }&gt;</code>

--------------------


### readSamples(...)

```typescript
readSamples(options: ReadSamplesOptions) => Promise<ReadSamplesResult>
```

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#readsamplesoptions">ReadSamplesOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#readsamplesresult">ReadSamplesResult</a>&gt;</code>

--------------------


### writeSamples(...)

```typescript
writeSamples(options: WriteSamplesOptions) => Promise<WriteSamplesResult>
```

| Param         | Type                                                                |
| ------------- | ------------------------------------------------------------------- |
| **`options`** | <code><a href="#writesamplesoptions">WriteSamplesOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#writesamplesresult">WriteSamplesResult</a>&gt;</code>

--------------------


### startMonitoring(...)

```typescript
startMonitoring(options: { types: HealthDataType[]; }) => Promise<void>
```

| Param         | Type                                      |
| ------------- | ----------------------------------------- |
| **`options`** | <code>{ types: HealthDataType[]; }</code> |

--------------------


### stopMonitoring()

```typescript
stopMonitoring() => Promise<void>
```

--------------------


### addListener('monitoringUpdate', ...)

```typescript
addListener(eventName: 'monitoringUpdate', listenerFunc: (data: { type: HealthDataType; value: number; }) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                                   |
| ------------------ | ------------------------------------------------------------------------------------------------------ |
| **`eventName`**    | <code>'monitoringUpdate'</code>                                                                        |
| **`listenerFunc`** | <code>(data: { type: <a href="#healthdatatype">HealthDataType</a>; value: number; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### Interfaces


#### PermissionStatusResult

| Prop        | Type                                                                                                                                            |
| ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| **`read`**  | <code><a href="#record">Record</a>&lt;<a href="#healthdatatype">HealthDataType</a>, <a href="#permissionstatus">PermissionStatus</a>&gt;</code> |
| **`write`** | <code><a href="#record">Record</a>&lt;<a href="#healthdatatype">HealthDataType</a>, <a href="#permissionstatus">PermissionStatus</a>&gt;</code> |


#### PermissionRequestOptions

| Prop        | Type                          |
| ----------- | ----------------------------- |
| **`read`**  | <code>HealthDataType[]</code> |
| **`write`** | <code>HealthDataType[]</code> |


#### ReadSamplesResult

| Prop          | Type                        |
| ------------- | --------------------------- |
| **`samples`** | <code>HealthSample[]</code> |


#### HealthSample

| Prop             | Type                                                                                         |
| ---------------- | -------------------------------------------------------------------------------------------- |
| **`type`**       | <code><a href="#healthdatatype">HealthDataType</a></code>                                    |
| **`value`**      | <code>number</code>                                                                          |
| **`unit`**       | <code><a href="#healthunit">HealthUnit</a></code>                                            |
| **`startDate`**  | <code>string</code>                                                                          |
| **`endDate`**    | <code>string</code>                                                                          |
| **`sourceName`** | <code>string</code>                                                                          |
| **`sourceId`**   | <code>string</code>                                                                          |
| **`metadata`**   | <code><a href="#record">Record</a>&lt;string, string \| number \| boolean \| null&gt;</code> |


#### ReadSamplesOptions

| Prop                  | Type                          |
| --------------------- | ----------------------------- |
| **`types`**           | <code>HealthDataType[]</code> |
| **`startDate`**       | <code>string</code>           |
| **`endDate`**         | <code>string</code>           |
| **`limit`**           | <code>number</code>           |
| **`ascending`**       | <code>boolean</code>          |
| **`includeMetadata`** | <code>boolean</code>          |


#### WriteSamplesResult

| Prop               | Type                 |
| ------------------ | -------------------- |
| **`success`**      | <code>boolean</code> |
| **`writtenCount`** | <code>number</code>  |


#### WriteSamplesOptions

| Prop          | Type                        |
| ------------- | --------------------------- |
| **`samples`** | <code>HealthSample[]</code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


### Type Aliases


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>


#### HealthDataType

<code>"steps" | "distance" | "calories" | "heart_rate" | "sleep"</code>


#### PermissionStatus

<code>"granted" | "denied" | "prompt" | "unsupported"</code>


#### HealthUnit

<code>"count" | "m" | "km" | "kcal" | "bpm" | "min" | "h"</code>

</docgen-api>
