import { initializeApp } from "firebase/app";
import {
  initializeAppCheck,
  ReCaptchaEnterpriseProvider,
  ReCaptchaV3Provider,
} from "firebase/app-check";
import { fetchAndActivate, getRemoteConfig, getValue } from "firebase/remote-config";
import { findingFor, firstStepFor, inspect, stepAfterDevice, type Finding, type Inspection } from "shineye-core";
import { deviceAvailability, downloadDeviceModel, Models, type Answer } from "./ai";
import {
  appCheckMode,
  defaultCloudModel,
  firebaseConfig,
  recaptchaSiteKey,
  type FirebaseOptions,
} from "./config";

type Step = "ASK_ON_DEVICE" | "ASK_CLOUD" | "OFFER_CLOUD" | "SETTLE";
type Consent = "ALWAYS" | "ASK" | "NEVER";

const hop = {
  rules: "Checked against known scam patterns in this browser",
  rulesEnough: "The patterns were clear enough, so no AI was needed",
  device: "Checked by Gemini Nano in this browser",
  deviceUnavailable: "Gemini Nano isn't available in this browser",
  deviceFailed: "Gemini Nano couldn't answer this time",
  offline: "You're offline, so the cloud check was skipped",
  cloud: "Checked by Gemini in the cloud, with personal details removed",
  declined: "You kept this message on your device",
  cloudFailed: "The cloud check couldn't be reached",
};

const el = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const form = el<HTMLFormElement>("check-form");
const message = el<HTMLTextAreaElement>("message");
const preview = el<HTMLElement>("preview");
const removed = el<HTMLElement>("removed");
const checkButton = el<HTMLButtonElement>("check");
const status = el<HTMLElement>("status");
const consentPanel = el<HTMLElement>("consent");
const consentPreview = el<HTMLElement>("consent-preview");
const consentNote = el<HTMLElement>("consent-note");
const alwaysSend = el<HTMLInputElement>("always-send");
const sendButton = el<HTMLButtonElement>("send");
const keepButton = el<HTMLButtonElement>("keep");
const result = el<HTMLElement>("result");
const deviceNote = el<HTMLElement>("device-note");
const downloadButton = el<HTMLButtonElement>("download");

if (location.hostname === "localhost") globalThis.FIREBASE_APPCHECK_DEBUG_TOKEN = true;

// The config is fetched rather than committed: /__/firebase/init.json is a
// reserved Firebase Hosting URL that returns the project's web config. A
// filled-in config.ts takes precedence during local development.
async function resolveConfig(): Promise<FirebaseOptions> {
  if (firebaseConfig.apiKey !== "REPLACE_ME") return firebaseConfig;
  try {
    const response = await fetch("/__/firebase/init.json");
    if (response.ok) return (await response.json()) as FirebaseOptions;
  } catch {
    // Fall through to the error below.
  }
  throw new Error("No Firebase configuration. Fill in web/src/config.ts for local development.");
}

const boot = (async () => {
  const app = initializeApp(await resolveConfig());
  initializeAppCheck(app, {
    provider:
      appCheckMode === "enterprise"
        ? new ReCaptchaEnterpriseProvider(recaptchaSiteKey)
        : new ReCaptchaV3Provider(recaptchaSiteKey),
    isTokenAutoRefreshEnabled: true,
  });
  const remote = getRemoteConfig(app);
  remote.defaultConfig = { cloud_model: defaultCloudModel, official_domains: "" };
  await fetchAndActivate(remote).catch(() => false);
  return { app, remote };
})();

let models: Models | undefined;
let official: string[] = [];

async function setup(): Promise<Models> {
  if (models) return models;
  const { app, remote } = await boot;
  official = getValue(remote, "official_domains").asString().split(",").map((d) => d.trim()).filter(Boolean);
  models = new Models(app, getValue(remote, "cloud_model").asString() || defaultCloudModel);
  return models;
}

function storedConsent(): Consent {
  try {
    const value = localStorage.getItem("shine-your-eye:consent");
    return value === "ALWAYS" || value === "NEVER" ? value : "ASK";
  } catch {
    return "ASK";
  }
}

function rememberAlways(): void {
  try {
    localStorage.setItem("shine-your-eye:consent", "ALWAYS");
  } catch {}
}

function renderRedacted(target: HTMLElement, text: string): void {
  target.replaceChildren(
    ...text.split(/(\[(?:PHONE|ACCOUNT|ID|CARD|CODE|NUMBER|EMAIL)\]|\/…)/).map((part, index) => {
      if (index % 2 === 0) return document.createTextNode(part);
      const tag = document.createElement("mark");
      tag.className = "tag";
      tag.textContent = part === "/…" ? "/ link path" : part.slice(1, -1).toLowerCase();
      return tag;
    }),
  );
}

function updatePreview(): void {
  const text = message.value;
  if (!text.trim()) {
    preview.textContent = "Start typing or paste a message. Personal details appear here as tags.";
    preview.classList.add("empty");
    removed.textContent = "";
    return;
  }
  const inspection = inspect(text, official);
  preview.classList.remove("empty");
  renderRedacted(preview, inspection.redacted.text);
  const gone = inspection.redacted.removed;
  removed.textContent = gone.length ? `Removed: ${gone.join(", ")}` : "Nothing personal found to remove.";
}

function busy(text: string | null): void {
  status.textContent = text ?? "";
  status.hidden = text === null;
  checkButton.disabled = text !== null;
}

function bullets(items: string[]): HTMLUListElement {
  const list = document.createElement("ul");
  list.append(...items.map((item) => Object.assign(document.createElement("li"), { textContent: item })));
  return list;
}

function show(finding: Finding, trail: string[]): void {
  busy(null);
  consentPanel.hidden = true;
  const heading = document.createElement("h2");
  heading.textContent = finding.headline;
  const advice = document.createElement("p");
  advice.className = "advice";
  advice.textContent = finding.advice;
  const how = document.createElement("h3");
  how.textContent = "How this was checked";
  const steps = document.createElement("ol");
  steps.append(...trail.map((step) => Object.assign(document.createElement("li"), { textContent: step })));
  const parts: HTMLElement[] = [heading, advice];
  if (finding.signals.length) parts.push(bullets(finding.signals));
  if (finding.modelReasons.length) {
    const model = document.createElement("h3");
    model.textContent = "What the AI noticed";
    parts.push(model, bullets(finding.modelReasons));
  }
  parts.push(how, steps);
  result.className = `result ${finding.kind.toLowerCase().replace("_", "-")}`;
  result.replaceChildren(...parts);
  result.hidden = false;
  result.focus();
}

function settle(inspection: Inspection, trail: string[], answer: Answer | null): void {
  show(findingFor(inspection, answer?.risk ?? null, answer?.confident ?? false, answer?.reasons ?? [], answer?.source ?? null), trail);
}

async function route(inspection: Inspection, device: boolean, online: boolean, consent: Consent, trail: string[]): Promise<void> {
  const step = firstStepFor(inspection, device, online, consent) as Step;
  if (step === "ASK_ON_DEVICE") return askDevice(inspection, online, consent, trail);
  if (step === "ASK_CLOUD") return askCloud(inspection, trail, null);
  if (step === "OFFER_CLOUD") return offerCloud(inspection, trail, null, device, online);
  settle(inspection, trail, null);
}

async function askDevice(inspection: Inspection, online: boolean, consent: Consent, trail: string[]): Promise<void> {
  busy("Checking with Gemini Nano in this browser…");
  let answer: Answer;
  try {
    answer = await (await setup()).onDevice(inspection);
  } catch {
    trail.push(hop.deviceFailed);
    return route(inspection, false, online, consent, trail);
  }
  trail.push(hop.device);
  const next = stepAfterDevice(inspection, online, consent, answer.risk, answer.confident) as Step;
  if (next === "ASK_CLOUD") return askCloud(inspection, trail, answer);
  if (next === "OFFER_CLOUD") return offerCloud(inspection, trail, answer, true, online);
  settle(inspection, trail, answer);
}

async function askCloud(inspection: Inspection, trail: string[], fromDevice: Answer | null): Promise<void> {
  busy("Getting a second opinion from the cloud…");
  try {
    const answer = await (await setup()).inCloud(inspection.redacted);
    trail.push(answer.source === "CLOUD" ? hop.cloud : hop.device);
    settle(inspection, trail, answer);
  } catch {
    trail.push(hop.cloudFailed);
    settle(inspection, trail, fromDevice);
  }
}

function offerCloud(inspection: Inspection, trail: string[], fromDevice: Answer | null, device: boolean, online: boolean): void {
  busy(null);
  checkButton.disabled = true;
  consentNote.textContent = fromDevice
    ? "Gemini Nano wasn't sure. A stronger check runs in the cloud, and only this copy would leave your device:"
    : "A stronger check runs in the cloud. Only this copy would leave your device:";
  renderRedacted(consentPreview, inspection.redacted.text);
  alwaysSend.checked = false;
  consentPanel.hidden = false;
  sendButton.focus();
  sendButton.onclick = () => {
    if (alwaysSend.checked) rememberAlways();
    consentPanel.hidden = true;
    void askCloud(inspection, trail, fromDevice);
  };
  keepButton.onclick = () => {
    consentPanel.hidden = true;
    trail.push(hop.declined);
    if (fromDevice) settle(inspection, trail, fromDevice);
    else void route(inspection, device, online, "NEVER", trail);
  };
}

async function check(): Promise<void> {
  const text = message.value;
  if (!text.trim()) return;
  result.hidden = true;
  busy("Checking against known scam patterns…");
  await setup();
  const inspection = inspect(text, official);
  const device = (await deviceAvailability()) === "available";
  const online = navigator.onLine;
  const trail = [hop.rules];
  if (inspection.floor === "HIGH") {
    trail.push(hop.rulesEnough);
    return settle(inspection, trail, null);
  }
  if (!device) trail.push(hop.deviceUnavailable);
  if (!online) trail.push(hop.offline);
  await route(inspection, device, online, storedConsent(), trail);
}

async function describeDevice(): Promise<void> {
  const availability = await deviceAvailability();
  downloadButton.hidden = availability !== "downloadable";
  deviceNote.textContent = {
    available: "Gemini Nano is ready in this browser, so messages are checked here first.",
    downloadable: "This browser can run Gemini Nano after a one-time download.",
    downloading: "Gemini Nano is downloading in this browser.",
    unavailable: "This browser can't run Gemini Nano, so the AI check uses the cloud, with your permission.",
  }[availability];
}

downloadButton.addEventListener("click", async () => {
  downloadButton.disabled = true;
  try {
    await downloadDeviceModel((fraction) => {
      deviceNote.textContent = `Downloading Gemini Nano: ${Math.round(fraction * 100)}%`;
    });
  } finally {
    downloadButton.disabled = false;
    await describeDevice();
  }
});

const examples: Record<string, string> = {
  scam: "Your account will be blocked. Send your OTP to 08031234567 now.",
  legit: "Hello, this is a notice from your bank about a recent transaction. Please review the details in the app.",
  pidgin: "Abeg wetin dey happen? My account show alert but balance no change. Call 08031234567.",
};

for (const button of document.querySelectorAll<HTMLButtonElement>("[data-example]")) {
  button.addEventListener("click", () => {
    const text = examples[button.dataset.example ?? ""];
    if (!text) return;
    message.value = text;
    updatePreview();
    void check();
  });
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  check().catch(() => {
    busy(null);
    status.hidden = false;
    status.textContent = "That check didn't finish. Check your connection, then try again.";
  });
});
message.addEventListener("input", updatePreview);
setup().then(updatePreview).catch(() => {});
updatePreview();
void describeDevice();
