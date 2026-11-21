export const firebaseConfig = {
  apiKey: "REPLACE_ME",
  authDomain: "shine-your-eye.firebaseapp.com",
  projectId: "shine-your-eye",
  storageBucket: "shine-your-eye.firebasestorage.app",
  messagingSenderId: "474113410192",
  appId: "1:474113410192:web:85e6d964de769da79f4415",
};

// App Check provider for the web app.
//
// "v3"         reCAPTCHA v3: free, no billing account (recommended for Spark).
// "enterprise" reCAPTCHA Enterprise / Fraud Defense: free tier, but Cloud
//              Billing must be enabled on the project.
//
// Register the web app in Firebase under App Check with the same provider,
// then paste the site key here. The secret key goes only into the Firebase
// console, never into this file.
export type AppCheckMode = "v3" | "enterprise";

export const appCheckMode: AppCheckMode = "enterprise";

export const recaptchaSiteKey = "6LfEwCsuAAAAAAyDM7NaNRke6r_FbOerlIy5za7x";

export const defaultCloudModel = "gemini-3.5-flash-lite";
