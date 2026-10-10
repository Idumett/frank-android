# Android release operations

Frank supports Android 9 (API 28) through Android 16 (API 36). Upgrades preserve
onboarding choices, confirmed places and preferences. Schema changes must migrate
forward without deleting user data. Downgrades are unsupported. Every Play upload
must use a larger `frankVersionCode`.

Google Play internal testing is the V1 distribution channel. It is private to the
email allow-list or tester group and provides an opt-in link. Play App Signing holds
the app-signing key; Frank keeps only the replaceable upload key.

The `Publish internal Android release` workflow builds a signed AAB and publishes
the binary and `distribution/whatsnew` listing text. Configure its `play-internal`
environment with:

- `FRANK_UPLOAD_KEYSTORE_BASE64`
- `FRANK_UPLOAD_STORE_PASSWORD`
- `FRANK_UPLOAD_KEY_ALIAS`
- `FRANK_UPLOAD_KEY_PASSWORD`
- `PLAY_SERVICE_ACCOUNT_JSON`
- `FRANK_SERVER_URL`
- `FRANK_ACCESS_TOKEN`

Every commit pushed to `main` publishes a new internal-testing build. These jobs are
serialized and receive a version code derived from the monotonic workflow run number.
The workflow can also run manually with an explicit, larger version code, or from
an `android-vX.Y.Z` tag. Keep the environment protected so only an approved release
can expose a new token to testers.

Before promoting beyond internal testing, complete the physical-device checklist,
publish a privacy policy, finish Play's Data safety and app-access declarations,
and replace the single demo identity with account-bound user identity.
