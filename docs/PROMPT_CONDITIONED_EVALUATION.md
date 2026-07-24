# Prompt-conditioned evaluation

## What is being measured

An AI model does not act in isolation. A deployed result is produced by a model under a complete
configuration: model version, decoding settings, tools, system or developer instructions, user
request, task state, and seed. vaLudus calls a study that varies and records these inputs a
**prompt-conditioned evaluation**.

The related research terms are *prompt sensitivity*, *prompt robustness*, and *prompt ablation*.
There is no useful model-only score when an application deliberately changes the context in which a
model operates. The model and the prompt should be measured separately and together.

## Required study matrix

For a fixed domain task and model version, a study should retain these named layers:

| Layer | Purpose | Examples |
|---|---|---|
| System configuration | Product-owned operating context | tool policy, safety policy, role instructions |
| User request | The user's own objective and wording | requested game goal, lesson goal, economic policy goal |
| Task state | Domain-owned world or fixture | board state, Mindustry world, lesson source, simulated economy |
| Candidate configuration | Model and runtime identity | model revision, temperature, tool implementation, seed |

Run at least these cells where applicable:

1. User request only, with no extra task-solving scaffold.
2. Product configuration plus the same user request.
3. Product configuration with a meaning-preserving user-request paraphrase.
4. An ablation that removes or changes one product-owned instruction at a time.
5. A held-out or adversarial task state under the same frozen configuration.

This permits three honest results: the quality of the deployed configuration, the incremental
effect of the product-owned prompt, and the stability of the result when the user says the same
thing differently.

## Reporting rules

Every run report must identify or hash each prompt layer, say which layers are public or held out,
and state the ordering and tool policy. Never publish private user prompts without consent. Do not
claim that a prompt lift is inherent model capability, and do not treat a paraphrase regression as a
model defect before checking whether the scorer recognizes semantically equivalent success.

For each metric, report the cell-level result, mean, range or variance across prompt variants, seed
variation, budget use, and invalid runs. A winner on the best prompt is not necessarily the most
reliable configuration.

## Domain application

- **Gambit:** compare a terse user goal such as "win safely" with controlled paraphrases and a
  product-owned planning scaffold, while holding the Capablanca Turnabout or Siamese state,
  handicap, opponent, clock, and rules revision fixed.
- **Mindustry:** preserve the player's goal separately from production or defense heuristics added
  by the product; score whether the scaffold helps across held-out disruptions rather than only a
  familiar map.
- **LunLunZhongWen:** distinguish the learner's requested level and topic from teaching structure
  supplied by the product, then evaluate factual accuracy and instructional usefulness.
- **Galactica society simulator:** hold institutional rules and world seed fixed while comparing
  agent policies and prompt configurations against explicit distributional and resilience outcomes.
