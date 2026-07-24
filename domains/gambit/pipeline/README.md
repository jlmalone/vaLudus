# Gambit pipeline placeholder

The future pipeline will replay a pinned position or match state against a declared Gambit rules
revision, invoke one candidate configuration, preserve every action and clock observation, score
the resulting state, and write a vaLudus run report. It must run offline and never access a
person's Knomee identity, cloud history, or live match.

The first implementation path is the [Capablanca family pipeline](CAPABLANCA_FAMILY.md).
