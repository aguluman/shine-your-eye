export interface FirebaseOptions {
  apiKey: string;
  authDomain: string;
  projectId: string;
  storageBucket: string;
  messagingSenderId: string;
  appId: string;
}

// A Firebase web config is not a credential. The API key identifies the
// project and is sent to every browser that loads the app; App Check and API
// key restrictions are what protect the project. It is kept out of git anyway,
// as defence in depth.
//
// On Firebase Hosting the reserved URL /__/firebase/init.json serves the real
// config, so production needs nothing here. For local development, paste the
// public API key below; it is safe to do so, just do not commit it.
export const firebaseConfig: FirebaseOptions = {
  apiKey: "REPLACE_ME",
  authDomain: "shine-your-eye.firebaseapp.com",
  projectId: "shine-your-eye",
  storageBucket: "shine-your-eye.firebasestorage.app",
  messagingSenderId: "474113410192",
  appId: "1:474113410192:web:85e6d964de769da79f4415",
};

// App Check provider for the web app.
//
// "v3"         reCAPTCHA v3
// "enterprise" reCAPTCHA Enterprise / Fraud Defense
//
// Register the web app in Firebase under App Check with the same provider,
// then paste the site key here. The secret key goes only into the Firebase
// console, never into this file.
export type AppCheckMode = "v3" | "enterprise";

export const appCheckMode: AppCheckMode = "enterprise";

export const recaptchaSiteKey = "6LfEwCsuAAAAAAyDM7NaNRke6r_FbOerlIy5za7x";

export const defaultCloudModel = "gemini-3.5-flash-lite";
