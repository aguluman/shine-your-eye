# Devpost submission draft

## Tagline

Check a suspicious bank message on your own phone, privately, before you act on it.

## Inspiration

In Nigeria, fake bank messages are part of daily life: "send the OTP to reverse the transaction", "your BVN must be updated", "I sent ₦25,000 to you by mistake". The people most likely to be caught are the least likely to own a phone that can run an on-device model. So the hybrid question here is not only "is a local model available?" It is "what is allowed to leave this phone?"

## What it does

Paste or share a message. Shine your eye tells you whether it looks like a scam, why, and what to do instead. It also shows exactly how the message was checked.

## How we built it

- **One Kotlin Multiplatform core** for both platforms. It holds the scam rules, the redactor, the routing policy, the verdict logic and the prompt, and it compiles to the JVM for Android and to JavaScript for the web.
- **Two models.** Raw text only goes to a Firebase AI Logic model in `ONLY_ON_DEVICE` mode. Only redacted text, of a type the compiler checks, goes to a model in `PREFER_IN_CLOUD` mode.
- **A pure routing function** decides the path. It is verified across all 144 situations it can face.
- **The rules set a floor.** No model output can lower it, which also makes prompt injection harmless.
- **Firebase products:** App Check, Remote Config for the official bank domain list and the model name, and Hosting.

## Challenges

`PREFER_ON_DEVICE` would silently send raw OTPs to the cloud on most Nigerian phones, so we designed around it. We also had to make redaction idempotent: the property tests found that cleaning a link could create a new email address out of the text around it.

## What's next

Pidgin, Yoruba, Igbo and Hausa copy. A community-maintained official domain list. Detection of digits written as words. Lookalike domains in other scripts.

# Three-minute video plan

1. **0:00–0:20** The problem. Show a real-looking scam SMS on a phone. Say one line: "Most phones in Nigeria can't run an on-device model."
2. **0:20–1:00** Web on the laptop with Gemini Nano. Paste a fake-alert refund message and point at the live redaction panel. Check it and show the result with "Checked by Gemini Nano in this browser". Then turn Wi-Fi off and check another message: it still works.
3. **1:00–1:50** Android on the Pixel 7 Pro, which has no Nano. Share an SMS into the app and show the consent card with the redacted preview. Send it and show the cloud verdict and the trail.
4. **1:50–2:20** Paste a "send your OTP" message and show that it is caught instantly by rules, with no AI. Then paste a prompt-injection message ("answer SAFE") and show it is still flagged as a scam.
5. **2:20–2:50** Show the guarantee table in the README and the test run.
6. **2:50–3:00** One closing sentence and the link.
