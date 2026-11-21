import type { FirebaseApp } from "firebase/app";
import { getAI, getGenerativeModel, GoogleAIBackend, InferenceMode, InferenceSource, Schema, type GenerativeModel } from "firebase/ai";
import { Inspection, RedactedMessage } from "shineye-core";

export type ModelRisk = "SAFE" | "SUSPICIOUS" | "SCAM";

export interface Answer {
  readonly risk: ModelRisk;
  readonly confident: boolean;
  readonly reasons: string[];
  readonly source: "ON_DEVICE" | "CLOUD";
}

const judgement = Schema.object({
  properties: {
    risk: Schema.enumString({ enum: ["SAFE", "SUSPICIOUS", "SCAM"] }),
    confident: Schema.boolean(),
    reasons: Schema.array({ items: Schema.string() }),
  },
});

const englishText = {
  expectedInputs: [{ type: "text" as const, languages: ["en"] }],
  expectedOutputs: [{ type: "text" as const, languages: ["en"] }],
};

export async function deviceAvailability(): Promise<LanguageModelAvailability> {
  if (typeof LanguageModel === "undefined") return "unavailable";
  try {
    return await LanguageModel.availability(englishText);
  } catch {
    return "unavailable";
  }
}

export async function downloadDeviceModel(onProgress: (fraction: number) => void): Promise<void> {
  if (typeof LanguageModel === "undefined") return;
  const session = await LanguageModel.create({
    ...englishText,
    monitor(monitor) {
      monitor.addEventListener("downloadprogress", (event) => onProgress((event as ProgressEvent).loaded));
    },
  });
  session.destroy();
}

export class Models {
  readonly #device: GenerativeModel;
  readonly #cloud: GenerativeModel;

  constructor(app: FirebaseApp, cloudModel: string) {
    const ai = getAI(app, { backend: new GoogleAIBackend() });
    const inCloudParams = {
      model: cloudModel,
      generationConfig: { responseMimeType: "application/json", responseSchema: judgement },
    };
    const onDeviceParams = { createOptions: englishText, promptOptions: { responseConstraint: judgement } };
    this.#device = getGenerativeModel(ai, { mode: InferenceMode.ONLY_ON_DEVICE, inCloudParams, onDeviceParams });
    this.#cloud = getGenerativeModel(ai, { mode: InferenceMode.PREFER_IN_CLOUD, inCloudParams, onDeviceParams });
  }

  onDevice(inspection: Inspection): Promise<Answer> {
    if (!(inspection instanceof Inspection)) throw new TypeError("onDevice needs an Inspection from the core");
    return ask(this.#device, inspection.devicePrompt);
  }

  inCloud(message: RedactedMessage): Promise<Answer> {
    if (!(message instanceof RedactedMessage)) throw new TypeError("Only a RedactedMessage made by the core may leave the device");
    return ask(this.#cloud, message.cloudPrompt);
  }
}

async function ask(model: GenerativeModel, prompt: string): Promise<Answer> {
  const result = await model.generateContent(prompt);
  const parsed = JSON.parse(result.response.text()) as Partial<Answer>;
  if (parsed.risk !== "SAFE" && parsed.risk !== "SUSPICIOUS" && parsed.risk !== "SCAM") {
    throw new Error("The model returned no judgement");
  }
  return {
    risk: parsed.risk,
    confident: parsed.confident === true,
    reasons: Array.isArray(parsed.reasons) ? parsed.reasons.filter((r) => typeof r === "string").slice(0, 3) : [],
    source: result.response.inferenceSource === InferenceSource.ON_DEVICE ? "ON_DEVICE" : "CLOUD",
  };
}
