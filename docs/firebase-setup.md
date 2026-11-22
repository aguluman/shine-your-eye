# Firebase setup

This is the one-time console work the app needs. Everything here is public
configuration.

Project: `shine-your-eye` (Spark / no-cost plan).

## 1. Web app

The web app is registered. `web/src/config.ts` holds only public
identifiers; the API key is not committed. On Firebase Hosting the app reads
`/__/firebase/init.json`, which serves the real config, so production needs no
setup. For localhost, paste the public API key into `config.ts` temporarily.

## 2. App Check for the web app

Firebase AI Logic refuses every request until the app is attested.

Use **reCAPTCHA Enterprise** (Fraud Defense). This project uses an existing
Enterprise key on the Spark plan; creating a new key may require enabling
Cloud Billing.

1. Google Cloud console, project `shine-your-eye` → **Security → reCAPTCHA**
   → **Create key**
   - Label: `shine your eye`
   - Platform: **Website**
   - Domains: `localhost`, `shine-your-eye.web.app`,
     `shine-your-eye.firebaseapp.com`
2. Copy the **site key** into `web/src/config.ts`:

   ```ts
   export const recaptchaSiteKey = "…";
   ```

3. Firebase console → **Security → App Check → Apps → web app →
   reCAPTCHA Enterprise**, paste the same site key, and save.

The site key is public by design. No secret key is used for this provider, so
nothing sensitive belongs in the repository.

### Localhost

`web/src/main.ts` sets `FIREBASE_APPCHECK_DEBUG_TOKEN = true` on localhost.
Run `make web-dev`, open the browser console, and copy the debug token it
prints. Add it under App Check → the web app → ⋮ → **Manage debug tokens**.

## 3. Android app

Firebase console → **Project settings → Your apps → Add app → Android**.

- Package name: `com.invarware.shineye`
- Nickname: `shine your eye`
- Debug signing certificate SHA-256:

  ```
  1C:01:73:4D:1B:F5:23:9D:C8:84:FD:07:6A:B9:2D:65:11:C3:6B:BB:EC:27:C3:0C:E4:2D:6C:DB:CA:CE:D3:97
  ```

Download `google-services.json` into `android/`. It is gitignored: every
developer supplies their own.

Then register App Check → Android app → **Play Integrity**.

Debug builds use the App Check debug provider, which prints a token in Logcat:

```
adb logcat | grep -i DebugAppCheckProvider
```

Add that token under App Check → the Android app → ⋮ → **Manage debug
tokens**.

## 4. AI Logic settings

On the AI Logic page:

- **Gemini Developer API**: enabled.
- **AI monitoring**: optional; safe to leave on. Only cloud requests are
  traced, and those only ever carry redacted text.
- **Agent Platform Gemini API**: leave off.
- **Template-only mode**: leave off; it would block the app's prompts.
- **Authenticated-users mode**: leave off; the app has no Firebase Auth.

## 5. Remote Config

`make deploy` publishes `remoteconfig.template.json`. It holds the cloud model
name and the official bank domains. Both the web app and the Android app read
them, so either can change without an app release.

## 6. Hosting

```
npm i -g firebase-tools
firebase login
make deploy
```

The site is served from `web/dist` at
<https://shine-your-eye.web.app>. Add that domain to the reCAPTCHA key and to
App Check before the judges visit it.
