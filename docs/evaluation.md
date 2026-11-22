# Measuring the rules

The rules run before any model, so they can be measured on their own. Keep the
corpus private: do not commit it, and do not paste messages into the repository
or the submission form.

## Corpus format

A TSV file, one message per line:

```
scam<TAB>Your account will be blocked. Send your OTP to 08031234567 now.
safe<TAB>Your account statement is ready. Open the bank app to view it.
```

Accepted labels: `scam`, `fraud`, `1`, `yes` for scams; `safe`, `legit`, `0`,
`no` for legitimate messages. Blank lines and lines starting with `#` are
ignored.

Keep it outside the repo, for example `~/private/scam-corpus.tsv`.

## Run it

```sh
SHINE_EVAL_CORPUS=$HOME/private/scam-corpus.tsv \
  ./gradlew :core:jvmTest --tests '*EvaluationTest*' --info
```

Illustrative output, not real results:

```
rules-only corpus report
  scams: 41   safe: 19
  caught (TP): 38   missed (FN): 3
  false alarms (FP): 2   correctly cleared (TN): 17
  precision: 0.950   recall: 0.927
```

The test always passes; it is a report, not a gate. Use the aggregate numbers in
the README and the submission. Never publish the messages themselves.

## What the number means

- **Precision**: of the messages it flagged, how many were actually scams.
- **Recall**: of the scams, how many it caught.
- The model handles what the rules miss. The README already documents that the
  rules set a floor and the model can only raise the verdict.
