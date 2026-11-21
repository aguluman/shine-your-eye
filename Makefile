.DEFAULT_GOAL := help

GRADLE := ./gradlew
CORE_JS_TASK ?= :core:jsNodeProductionLibraryDistribution

.PHONY: help wrapper test core-js web-install web-dev web-build android-debug deploy clean

help:
	@echo "Shine your eye"
	@echo ""
	@echo "  make wrapper        create the Gradle wrapper (Gradle 9.9.1)"
	@echo "  make test           run the core tests on the JVM and Node"
	@echo "  make core-js        build the core as an ES module for the web app"
	@echo "  make web-install    install web dependencies"
	@echo "  make web-dev        serve the web app on localhost with live rebuilds"
	@echo "  make web-build      type-check and bundle the web app into web/dist"
	@echo "  make android-debug  install the debug Android app on a connected phone"
	@echo "  make deploy         deploy the web app and Remote Config to Firebase"
	@echo "  make clean          remove build output"

wrapper:
	@./gradlew wrapper --gradle-version 9.9.1

test:
	@$(GRADLE) :core:allTests

core-js:
	@$(GRADLE) $(CORE_JS_TASK)

web-install: core-js
	@cd web && npm install

web-dev: web-install
	@cd web && npm run dev

web-build: web-install
	@cd web && npm run build

android-debug:
	@$(GRADLE) :android:installDebug

deploy: web-build
	@firebase deploy --only hosting,remoteconfig

clean:
	@$(GRADLE) clean && rm -rf web/dist
