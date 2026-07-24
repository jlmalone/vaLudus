# Gambit domain

Gambit is a game-play and rule-reasoning evaluation domain. It should distinguish legality,
planning quality, clock use, and match outcomes; a legal-move score alone is not game-playing
strength. Its first executable target is the Capablanca Turnabout and Capablanca Siamese family,
including material handicaps.

The existing Siamese Capablanca rule gate remains at
`benchmarks/gambit-siamese-capablanca/` while this domain-local structure is established. It is a
public regression gate, not an unseen-strength benchmark.

See [pipeline](pipeline/README.md), [generator](generator/README.md), and
[benchmarks](benchmarks/README.md).
