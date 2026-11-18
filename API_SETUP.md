# Cedi Plus API setup

The app reads private values from the ignored `local.properties` file. Keep the assigned signing key and Meta app ID out of source control.

```properties
cedi.signingKey=YOUR_ASSIGNED_PACKAGE_SIGNING_KEY
cedi.facebookAppId=YOUR_META_APP_ID
cedi.facebookClientToken=YOUR_META_CLIENT_TOKEN
cedi.apiBaseUrl=https://api-ghana.kamfams.com
cedi.headerPackage=tuopes.nmbesf.truehgc
```

`cedi.apiBaseUrl` defaults to the Ghana test host. Requests use POST form bodies and append the interface code from `BJX-GH-Api.md` to that host, for example `/2.3.1.0`. The form and header keys are the mapped values in `Cedi Plus接口参数映射.xls`. The signature is MD5 uppercase over the non-empty form values in mapped-key ASCII order, followed by the `lc` signing-key parameter shown in the API guide.

`cedi.headerPackage` supplies the mapped `bag` header value and defaults to the package name shown in the API guide. Set it to the exact package assigned by the API provider if that value differs.

Facebook registration and first-loan events remain disabled until both `cedi.facebookAppId` and `cedi.facebookClientToken` are configured. Adjust uses the existing app token and event tokens. AWS upload credentials are fetched again for every image upload; objects are uploaded with the AWS SDK for Kotlin using the temporary access key, secret, and session token returned by the API.

The project declares only the permissions listed in the integration brief. Phone state is required to enter the app; camera, SMS, and coarse location can be declined at first launch, while SMS and coarse location are required again before applying. Calendar permission is not declared because it is absent from the allowed permission list.
