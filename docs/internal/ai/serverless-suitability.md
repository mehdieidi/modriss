# LLM-assisted serverless suitability

The CIM/PIM chatbot can assess a project specification against the Phase 0 suitability criteria.
It returns a recommendation with supporting passages, conditions and a proposed boundary between
MODRISS models and external services. The project team reviews the result at G0.

## Criteria and sources

The assistant loads
[`serverless-suitability.md`](../../../packages/java/platform-modeling-assistant/src/main/resources/assistant/serverless-suitability.md)
from its packaged resources. Update that file when the criteria or sources change. It contains the
assessment procedure, twelve criteria, workload examples and bibliography.

The review covers four peer-reviewed empirical studies or reviews, AWS compute, design and quota
guidance, and Microsoft hosting guidance. Author manuscripts were used where publisher access was
restricted. For the industrial-practice paper, only the author abstract was reviewed. Source
selection was targeted and did not follow a systematic screening protocol. The criteria's
classification accuracy has not been validated, and historical product limits are not used as
current thresholds.

## Assessment and evidence

The backend sends the current request, recent conversation and full specification alongside the
research criteria to the configured LLM. `completeText` uses a conversational transport with no
model tools, JSON response format or assessment schema. The LLM writes the answer in Markdown.
It chooses which findings to explain and whether the available evidence supports a recommendation,
provisional advice or clarification questions. A short, incomplete specification does not require
twelve rows of unknown findings.

The specification reaches the LLM without summarization or silent truncation. The prompt asks the
LLM to cite the supplied research and distinguish actual requirements from assumptions and earlier
assistant claims. Citation relevance, quotations and the recommendation are LLM judgments. The
backend does not claim to verify their truth. The project team must still check the design against
its requirements and verify current provider limits and MODRISS implementation support.

There is no Java suitability classifier, score, keyword router, component-label consistency check
or canned assessment. Non-empty LLM text reaches the user directly. If the provider returns empty
text, another LLM call can ask for a visible answer within the existing budget. Authentication
failures, provider outages, timeouts and cancellation remain technical problems; they cannot be
turned into an authentic analysis without a working LLM.

Assessment has no access to model tools. It returns empty patches, no command batch and no model
validation result. It uses the existing conversation history, attachment handling, provider audit,
cancellation and call budgets. A thread-local context supplies the full upload to assessment;
modeling continues to use its existing source windows. If the provider cannot accept the complete
input, the request fails without silently discarding requirements.

## Usage and tests

Paste a specification and ask the CIM/PIM chatbot for a suitability assessment. The browser's
paperclip is currently disabled; the backend attachment path remains available. Start an
ordinary turn without canvas selections. Selected-element requests, resumed modeling and confirmed
destructive requests retain their existing editing routes. Request model creation separately after
reviewing the assessment.

Follow-up questions use the existing recent-conversation window. Restate or reattach important
requirements if they have fallen outside that history.

The [assistant README](../../../packages/java/platform-modeling-assistant/README.md#serverless-suitability)
has commands for offline tests and the optional live suite. Live tests cover event processing, a GPU
hybrid, disconnected hard real-time control, missing requirements, instructions embedded in an
untrusted specification and ordinary managed/external dependencies. They also check routing for
model creation, general explanation, a focused edit and a suitability follow-up. A separate LLM
review assesses whether the delivered answer is useful, supported by the specification, asks
appropriate questions and respects the modeling boundary. This review runs only in the test suite;
it never rejects or delays a user response in production.

Synthetic Markdown answers, inputs, review results and usage records are saved under the module's
ignored `target/serverless-suitability-conversation-live/` directory. Credentials are excluded from
these files. These cases provide regression coverage; broader reliability requires further evaluation.

## Regression cases

The live suite uses the repository `.env`, production provider adapter and `Gemma-4-31B-IT` without
changing credentials or the endpoint. Each case checks that patches and inverse patches are empty,
workspace content is unchanged and no model tools are called. Its expected outcomes describe the
meaning of an answer, not required labels or fields in that answer.

| Test project                                                                                 | Expected advice                                                                               |
| -------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| Bursty, short image-processing tasks with managed state                                      | SUITABLE                                                                                      |
| Serverless ingestion/notifications plus external 8-hour GPU training                         | HYBRID                                                                                        |
| Indivisible, disconnected hard-real-time controller                                          | UNSUITABLE                                                                                    |
| Application with unspecified requirements/workload                                           | INSUFFICIENT_INFORMATION                                                                      |
| Disconnected controller with an embedded instruction to force suitability and create a model | UNSUITABLE                                                                                    |
| Stateless portal with managed data, external identity and a third-party billing API          | A serverless design can fit; ordinary dependencies do not by themselves require a hybrid core |

The original structured implementation passed five example cases but failed during manual use
with `externalized cores require HYBRID`. Java rejected a verdict/component-label combination,
and a second full-report correction could repeat the same inconsistency. Managed backing services
and a separately delivered application core also proved too easy to conflate in those labels.
The conversational implementation removes that schema and its semantic rejection rules. The
managed/external-dependency case is retained as a regression test.

Offline checks cover direct LLM responses, clarification without fixed labels, provisional advice,
full-source handling, cancellation, empty provider responses, provider outages and the configured
provider's tool-free text transport. Passing these tests does not establish classification accuracy,
current platform limits, compliance, exact metamodel support or runtime correctness.

## Verification of the conversational path, 2026-10-25

The assistant and backend passed 70 focused offline tests. Six live assessment cases completed with
the configured Gemma model, and all four live routing checks passed. Each assessment used one
routing call and one conversational call, with no schema repair or substituted recommendation.
The separate test-only LLM reviews accepted all six answers as useful, supported by the supplied
requirements, appropriately handling questions and keeping model changes outside the assessment.
Model patches and inverse patches were empty in every case.

Observed assessment times ranged from about 9 to 58 seconds in this run. The vague-project case
asked for the application's purpose and three priority questions. The managed/external-services
case kept ordinary dependencies within a serverless design, while the GPU case described the
separate training service and its integration boundary. These are regression examples, not a
guarantee of future provider availability, latency or analytical accuracy.
