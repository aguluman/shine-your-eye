type LanguageModelAvailability = "unavailable" | "downloadable" | "downloading" | "available";

type TextModality = { type: "text"; languages?: string[] };

interface LanguageModelCreateOptions {
  expectedInputs?: TextModality[];
  expectedOutputs?: TextModality[];
  monitor?: (monitor: LanguageModelMonitor) => void;
}

interface LanguageModelMonitor extends EventTarget {}

interface LanguageModelSession {
  destroy(): void;
}

interface LanguageModelStatic {
  availability(options?: object): Promise<LanguageModelAvailability>;
  create(options?: LanguageModelCreateOptions): Promise<LanguageModelSession>;
}

declare var LanguageModel: LanguageModelStatic | undefined;

declare var FIREBASE_APPCHECK_DEBUG_TOKEN: boolean | string | undefined;
