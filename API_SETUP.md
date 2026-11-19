# Cedi Plus API setup

The app reads private values from the ignored `local.properties` file. Keep the assigned signing key and Meta app ID out of source control.

```properties
cedi.signingKey=YOUR_ASSIGNED_PACKAGE_SIGNING_KEY
cedi.facebookAppId=YOUR_META_APP_ID
cedi.facebookClientToken=YOUR_META_CLIENT_TOKEN
cedi.apiBaseUrl=https://api-ghana.kamfams.com
cedi.headerPackage=tuopes.nmbesf.truehgc
cedi.apiLogEnabled=false
```

Set `cedi.apiLogEnabled=true` in `local.properties` and rebuild to print API route, request field names, HTTP status, elapsed time, and business result code to Logcat under the `CediApi` tag. It defaults to `false`. Debug builds also log response bodies for other routes, with long bodies split into numbered parts; the 2.3.1.10 AWS upload-credentials response is redacted. S3 upload failures log exception class names and cause types only, not error messages or credential values. Treat other response-body logs as sensitive.

`cedi.apiBaseUrl` defaults to the Ghana test host. Requests use POST form bodies and append the `mappingUri` from `Cedi Plus接口映射.xlsx`, selected by matching its `apiCode` to the interface section in `BJX-GH-Api.md`. For example, `2.3.1.0` (首次启动时访问) uses `/qXZpWf/UfhvkM/kjPkSK`. The form and header keys are the mapped values in `Cedi Plus接口参数映射.xls`. The signature is MD5 uppercase over the non-empty form values in mapped-key ASCII order, followed by the `lc` signing-key parameter shown in the API guide.

The anonymous-event API is titled `2.3.1.98` in the guide, while the mapping sheet lists the same API name under `2.3.1.99`; the app uses that row's `mappingUri` (`/qXZpWf/dsGgZh/zuDgEf`).

`cedi.headerPackage` supplies the mapped `bag` header value and defaults to the package name shown in the API guide. Set it to the exact package assigned by the API provider if that value differs.

Use the signing key assigned to that same header package. Set `cedi.signingKey` to the raw key value. The client adds the `lc=` prefix. The key in the guide is for its MD5 example.

The test gateway can return HTTP 200 with an inner status in `statusCodeValue` and an inner payload in `body`. The app reports gateway failures separately from business result codes. A gateway response of `400` with `sign error` indicates signature rejection. A response of `400` with `deviceInfo` indicates device header rejection.

Facebook registration and first-loan events remain disabled until both `cedi.facebookAppId` and `cedi.facebookClientToken` are configured. Adjust uses the existing app token and event tokens. AWS upload credentials are fetched again for every image upload; objects are uploaded with the AWS SDK for Kotlin using the temporary access key, secret, and session token returned by the API.

The project declares only the permissions listed in the integration brief. The first-launch Permissions page requests phone state, camera, calendar, SMS, and coarse location in that order. Phone state is required to continue; optional permissions may be skipped or denied. Before applying, phone state, calendar, SMS, and coarse location are required, while camera is exempt and is checked before each photo capture. A user who declines a required Apply permission is sent to app settings, and permissions are checked again when the app resumes.
