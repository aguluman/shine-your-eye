type LanguageModelAvailability = "unavailable" | "downloadable" | "downloading" | "available";

interface LanguageModelMonitor extends EventTarget {}

interface LanguageModelStatic {
  availability(options?: object): Promise<LanguageModelAvailability>;
  create(options?: { monitor?: (monitor: LanguageModelMonitor) => void }): Promise<{ destroy(): void }>;
}

declare var LanguageModel: LanguageModelStatic | undefined;

declare var FIREBASE_APPCHECK_DEBUG_TOKEN: boolean | string | undefined;
