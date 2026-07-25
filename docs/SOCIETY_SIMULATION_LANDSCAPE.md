# Society simulation landscape

Verified: 2026-07-25

This document records the simulation projects considered while scoping a possible vaLudus society
environment. The intended environment would combine synthetic people's daily activities, social
relationships, interests, economic constraints, institutions, media exposure, trends, fads, and
propaganda.

No project below provides an accepted predictive model of a whole society. Each represents selected
mechanisms under assumptions chosen by its authors. vaLudus should treat outputs from these systems
as results inside modeled worlds, not forecasts or evidence about particular real people.

## Closest generative society projects

### AgentSociety 2

[AgentSociety 2 documentation](https://agentsociety2.readthedocs.io/en/latest/) describes an
integrated research environment for executable social science. Its published examples span
individual experiments, social-media dynamics, and urban scenarios. The earlier
[AgentSociety paper](https://arxiv.org/abs/2502.08691) includes experiments involving political
polarization, inflammatory messages, universal basic income, and disaster shocks.

This is the closest surveyed project to a coupled LLM-driven society environment. Its scale and
range make it valuable for architecture and experiment-design study. Behavioural fidelity,
calibration, model dependence, and reproducibility remain evaluation questions rather than
properties to assume.

### Concordia

[Concordia](https://github.com/google-deepmind/concordia) is Google DeepMind's library for
generative agent-based simulation. Agents act in physical, social, or digital environments, while a
Game Master resolves intended actions against the modeled world. Its component model supports
memory, reasoning, social interaction, and different environment rules.

Concordia is a construction framework rather than a calibrated society. Its separation between
actors and world resolution is especially relevant to vaLudus: candidate intelligence should not
silently control the rules that judge whether its proposed action succeeds. See also the
[DeepMind research description](https://deepmind.google/research/publications/64717/).

### Project Sid

[Project Sid](https://arxiv.org/abs/2411.00114) reports many-agent experiments in a
Minecraft-derived environment. The project studies autonomous agents forming roles, trade,
coordination, government, culture, and religion. The
[project repository](https://github.com/altera-al/project-sid) contains its public materials.

Project Sid is pertinent to open-ended emergence and long-running social coordination. It is less
directly suited to controlled social-science claims unless its world rules, agent configurations,
interventions, and outcome measures can be made reproducible and independently challenged.

## Daily activity, mobility, and urban life

### OpenCity

[OpenCity](https://arxiv.org/abs/2410.21286) focuses on large-scale simulation of LLM agents'
daily urban activities. It reports experiments with 10,000 agents and activity data from six
cities, using daily planning and request-reduction techniques to make the simulation tractable.

OpenCity is relevant to schedules, activity choice, interests, and movement through a city. It does
not by itself supply the economic, institutional, cultural, or media layers of a complete society.

### MATSim

[MATSim](https://www.matsim.org/) is an open-source, large-scale multi-agent transport simulator.
It models individual activity schedules and travel across road and public-transport networks,
including interaction through congestion and repeated plan optimization.

MATSim offers a mature reference for time, schedules, capacity, mobility evidence, and metropolitan
scale. Its specialized scope is a strength: a vaLudus society model could learn from or interoperate
with its activity model without treating transport behavior as a complete theory of a person.

## Information, propaganda, and fads

### OASIS

[OASIS](https://github.com/camel-ai/oasis) is an open-source social-media simulator using LLM
agents. It models dynamic social networks, posts, following, commenting, reposting, and
recommendation systems at scales reported up to one million agents. Its experiments include
information spreading, polarization, and herd effects. See the
[OASIS paper](https://arxiv.org/abs/2411.11581).

OASIS is the most directly relevant surveyed system for trends, fads, propaganda, and
algorithm-mediated attention. It models a social-media environment, not the rest of daily life.
For vaLudus, message provenance, targeting, recommendation exposure, attention, belief change, and
recovery should remain distinct measurable stages.

## Economic, policy, and life-course models

### Dynare

[Dynare](https://www.dynare.org/about/) is a platform used for macroeconomic models including
dynamic stochastic general equilibrium, overlapping-generations, heterogeneous-agent, and
semi-structural models. It supports simulation, estimation, forecasting, policy analysis, and
sensitivity work.

Dynare represents the equation-based economics tradition rather than a synthetic population living
individual daily lives. It is valuable for macroeconomic baselines and for checking whether
agent-based results contradict established aggregate models under comparable assumptions.

### Econ-ARK and HARK

[HARK](https://docs.econ-ark.org/index.html), the Heterogeneous Agents Resources and toolKit, is a
Python toolkit for structural models of optimizing and non-optimizing heterogeneous agents. It
supports consumption-saving, portfolio, income-shock, life-cycle, and heterogeneous-agent
macroeconomic models.

HARK is relevant to household decision rules and micro-to-macro aggregation. It should be treated
as a source of tested economic model structures or an independent comparison implementation, not
as a ready-made cultural or political society.

### EURACE

[EURACE@Unibi](https://www.uni-bielefeld.de/fakultaeten/wirtschaftswissenschaften/lehrbereiche/etace/eurace%40unibi/)
extends the European Union's EURACE agent-based macroeconomic project. It models a spatial economy
with households, firms, labour, consumption goods, capital goods, finance, government, and policy.
The original [EURACE paper](https://doi.org/10.1016/j.amc.2008.05.116) describes its large-scale
agent architecture.

EURACE is a strong precedent for coupling multiple economic markets and institutions. It does not
attempt the richer attention, culture, propaganda, and daily-interest model under consideration
for vaLudus.

### PolicySpace2

[PolicySpace2](https://www.jasss.org/25/1/8.html) is an open-source, spatial agent-based model
grounded in data from Brazilian metropolitan regions. It includes households, firms, real estate,
labour, credit, goods and services, municipal taxation, and public-policy experiments.

PolicySpace2 is particularly useful as a precedent for tying heterogeneous agents and markets to
empirical geography while retaining explicit policy interventions. Its conclusions remain bounded
to its modeled mechanisms, calibration, and covered regions.

### JAS-mine

[JAS-mine](https://www.essex.ac.uk/centres-and-institutes/microsimulation-and-policy-analysis) is
a Java-based platform for dynamic microsimulation and agent-based modeling. The surrounding
research programme covers tax and benefits, family, gender, health, wellbeing, employment,
population change, and distributional policy effects over the life course. The
[platform paper](https://microsimulation.pub/V10_1/IJM_2017_10_1_4.pdf) describes its
data-driven discrete-event architecture.

JAS-mine is relevant to longitudinal synthetic populations and Kotlin/JVM implementation choices.
It provides a better precedent for life-course transitions than for open-ended LLM behaviour.

## General simulation toolkits

### NetLogo

[NetLogo](https://www.netlogo.org/about/) is a widely used environment for agent-based modeling of
natural and social phenomena. It is approachable, interactive, and supported by an extensive model
library.

NetLogo is well suited to rapid mechanism prototypes and visual inspection. A vaLudus production
environment would still need stronger artifact contracts, event evidence, scale controls, and
headless reproducibility.

### Mesa

[Mesa](https://github.com/mesa/mesa) is an open-source Python library for agent-based modeling. It
provides agent scheduling, spaces, data collection, batch execution, and browser-based
visualization.

Mesa is useful for studying mainstream ABM APIs and experiment workflows. It is not itself a
social or economic theory.

### SimPy

[SimPy](https://simpy.readthedocs.io/en/stable/index.html) is a process-based discrete-event
simulation framework for Python. It models processes, events, shared resources, queues, and
time-dependent interactions.

Its event and resource semantics are relevant to daily schedules, institutions, services, and
supply constraints. vaLudus can adopt those ideas without adopting Python as its implementation
language.

### Agents.jl

[Agents.jl](https://juliadynamics.github.io/Agents.jl/stable/) is a Julia framework for
agent-based modeling with discrete, continuous, graph, and event-driven spaces. It supports batch
experiments and data collection.

Agents.jl is useful for independent numerical prototypes and comparison with a Kotlin engine,
especially where Julia's scientific ecosystem is valuable.

### GAMA

[GAMA](https://gama-platform.org/) is an agent-based modeling environment oriented toward
spatially explicit and multi-level simulations. It provides a dedicated modeling language,
geographic support, visualization, parameter exploration, and batch experiments.

GAMA is most relevant when geography, land use, environmental systems, and spatial policy become
first-class parts of a society experiment.

## Implications for vaLudus

The surveyed work suggests that the difficult research problem is not creating agents that produce
plausible stories. It is coupling multiple social layers without losing falsifiability.

A vaLudus society environment should therefore separate:

1. a deterministic Kotlin event kernel for time, identity, resources, places, institutions, and
   durable evidence;
2. versioned behavioral models for schedules, economic choices, attention, belief updates, and
   relationships;
3. optional candidate intelligence behind an isolated adapter boundary;
4. declared interventions such as shocks, policies, recommendation changes, propaganda, and
   corrections;
5. measurements at individual, network, institutional, distributional, and aggregate levels; and
6. calibration, competing-model, sensitivity, and counterfactual runs.

The near-term role of these projects is architectural and methodological comparison. vaLudus should
not import a framework merely because it already simulates one layer. First specify the initial
world, the bounded claim, the evidence needed to falsify it, and which independent implementation
will serve as a comparison.
