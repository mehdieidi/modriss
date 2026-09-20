# Why Serverless Software Development Calls for a Model-Driven Methodology

## Abstract

Serverless computing is often introduced as an execution model that removes the
need to administer servers. That description is useful, but incomplete from a
software-engineering perspective. A serverless system is normally assembled
from fine-grained functions, events, managed data services, identity policies,
workflows, and provider-specific deployment resources. Its behaviour and cost
emerge from the interaction of these elements and from decisions that are not
contained in the function code alone. This report develops two connected
arguments. First, serverless computing changes a sufficiently broad set of
project conditions to justify a deliberately engineered and tailorable software
development method. Second, many of the resulting problems concern abstraction,
consistency, traceability, automation, and the relationship between
platform-independent intent and platform-specific realization. These are
problems for which model-driven engineering is particularly well suited. The
conclusion is not that every serverless project should follow one fixed process,
nor that modeling removes the need for implementation and operational judgment.
Rather, serverless constitutes a recognizable development situation, and a
model-driven method provides a defensible way to organize and automate work
within that situation.

## 1. The basis of the argument

A software development process should not be chosen independently of the setting
in which it will be used. Situational method engineering starts from this
premise. Instead of treating a published method as universally applicable, it
constructs or configures a method from reusable parts so that its activities,
roles, work products, and guidance fit a particular project situation (Asadi
and Ramsin, 2009). This position is consistent with the broader method-
engineering view that a software development methodology is itself an
engineered artifact: its requirements should be elicited, justified, and
evaluated, just as the requirements of the software product are (Ramsin and
Paige, 2010).

Clarke and O'Connor provide a useful empirical foundation for deciding what
counts as a development situation. Their synthesis identifies 44 situational
factors arranged in eight classes: **personnel, requirements, application,
technology, organization, operation, management, and business**. The factors
include, among others, team experience and cohesion, requirements changeability
and risk, architectural and performance characteristics, technological novelty,
organizational maturity, operational constraints, management expertise,
external dependencies, time to market, and potential loss. Their central
finding is not that one factor mechanically selects one process. It is that an
appropriate process is contingent on a combination of contextual conditions;
no single process is useful in every setting (Clarke and O'Connor, 2012).

This gives the present argument a clear test. If serverless were merely a
different hosting option, affecting deployment but leaving the rest of the
project essentially unchanged, a new or tailored method would be difficult to
justify. If, however, it alters several classes of situational factors and those
changes require different decisions, competencies, evidence, and coordination,
then serverless is methodologically significant.

## 2. Why serverless development needs an engineered method

### 2.1 Serverless changes the development situation

Function-as-a-Service (FaaS) moves allocation, scaling, and much of the runtime
stack behind a provider interface. It therefore removes some traditional
infrastructure work, but it does not remove engineering responsibility. The
developer now works with short-lived executions, event sources, managed
services, externalized state, permissions, quotas, retries, and pricing rules.
The unit of deployment becomes finer grained, while important system properties
emerge from connections among many independently configured resources.

Industrial evidence supports this interpretation. Leitner et al.'s mixed-method
study found that FaaS encourages a composition-oriented style in which managed
services provide substantial parts of the system and functions connect them.
The same study reported immature support for testing and deployment, difficulty
in predicting cost for larger applications, latency concerns, and conscious
trade-offs between provider lock-in and operational benefits (Leitner et al.,
2019). Wen et al. analyzed 22,731 developer questions and derived a taxonomy of
36 challenge categories spanning design, implementation, deployment,
configuration, security, data access, versioning, and language support (Wen et
al., 2021). An examination of almost 2,000 open-source serverless applications
likewise concluded that development-process support remained seriously lacking,
particularly for debugging, reuse, flexibility across providers, and the
integration of supporting services (Eskandani and Salvaneschi, 2023).

The relevance of these findings to the situational-factor framework can be made
explicit.

| Situational class | Change introduced or intensified by serverless                                                                                                                                                                                                            | Consequence for the development method                                                                                                                                                                          |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Personnel         | Teams need competence in event-driven design, managed services, identity and access management, infrastructure as code, observability, and consumption-based cost. A composition-oriented mental model differs from conventional application development. | The method needs explicit responsibilities, capability assessment, training or technical spikes, and collaboration across development, operations, security, and cost-management concerns.                      |
| Requirements      | Latency, burst behaviour, delivery semantics, idempotency, data residency, quotas, recovery, and cost ceilings become architecturally significant requirements. Some are difficult to settle before workload evidence exists.                             | Requirements work must include measurable service-level objectives, workload and cost assumptions, compliance constraints, uncertainty, and acceptance evidence; these must be revisited with operational data. |
| Application       | The system is distributed and event driven; state commonly resides outside functions; failures, retries, concurrency, and function composition affect end-to-end behaviour.                                                                               | Analysis and design must cover event flows, state ownership, function boundaries, contracts, orchestration or choreography, failure handling, and suitability for serverless—not only function code.            |
| Technology        | FaaS platforms and managed services evolve quickly and expose different limits, events, workflow languages, identity mechanisms, and deployment formats. Tool support is uneven.                                                                          | The method needs provider evaluation, proof-of-concept work, technology-decision records, compatibility controls, and a defined response to platform evolution.                                                 |
| Organization      | Effective use commonly crosses application development, platform engineering, security, operations, and financial governance. Organizational cloud maturity influences what can safely be delegated or automated.                                         | Tailoring must consider team topology, decision rights, separation of duties, platform guardrails, and the availability of shared capabilities.                                                                 |
| Operation         | The provider operates servers, but the customer still owns service objectives, telemetry, incident response, data protection, configuration, and expenditure. Cold starts and provider-side scaling can affect user-visible behaviour.                    | Observability, cost monitoring, resilience testing, incident learning, runbooks, and operational feedback have to be part of the lifecycle rather than post-deployment additions.                               |
| Management        | Fine-grained resources increase dependency and configuration coordination. Performance and cost are workload dependent, and frequent deployment raises release and recovery demands.                                                                      | Risk, dependency, configuration, release, quality, and change management require serverless-specific evidence and automation.                                                                                   |
| Business          | Pay-per-use pricing can improve economics for some workloads, while provider dependence, uncertain demand, service availability, and switching cost influence the business case.                                                                          | The method must begin with suitability and value analysis and retain cost forecasts, provider assumptions, lock-in decisions, and exit or retirement considerations.                                            |

The table also explains why the popular term _NoOps_ is misleading when treated
as a process prescription. Operational work is partly transferred to the cloud
provider and partly transformed. Teams may stop patching operating systems, yet
they must still decide what to observe, how to diagnose distributed executions,
how to recover from partial failure, and how to control permissions and cost.
Serverless changes the boundary of responsibility; it does not abolish that
boundary.

Security provides a clear example. Fine-grained functions frequently call
multiple managed services, each through configured identities and permissions.
The resulting attack surface is shaped by event sources, inter-service
authorization, secrets, dependencies, and configuration. These concerns demand
threat analysis, least-privilege design, configuration checks, and operational
monitoring. They cannot be addressed by a generic instruction to “implement the
functions.” Performance shows the same pattern. Cold-start delay, platform
limits, concurrency, data location, and downstream-service behaviour may all
contribute to end-to-end latency. A meaningful process must connect performance
requirements to architectural decisions, testing conditions, deployment
configuration, and observed runtime evidence.

### 2.2 From situational change to method requirements

The method-engineering conclusion follows in three steps:

1. The suitability of a software process depends on the project's situational
   factors (Clarke and O'Connor, 2012).
2. Serverless changes factors across all eight situational classes, and
   empirical studies show that these changes create recurring development and
   operational difficulties (Leitner et al., 2019; Wen et al., 2021; Eskandani
   and Salvaneschi, 2023).
3. Those difficulties require explicit tasks, roles, work products, decision
   points, and feedback mechanisms that a generic process may not supply.

It is therefore reasonable to treat serverless development as a distinct
method-engineering situation. This does not prove that every project needs a
wholly original lifecycle. The stronger and more defensible claim is that the
process must be **configured for serverless concerns**. Reusable practices from
iterative development, DevOps, risk management, security engineering, and
operations can remain, while serverless-specific fragments are added for
suitability analysis, event and function design, provider selection, cost and
performance reasoning, infrastructure generation, deployment, observability,
and evolutionary change. Situational method engineering supplies the mechanism
for assembling and tailoring those fragments; criteria-based requirements
engineering supplies the mechanism for explaining why each one is present
(Asadi and Ramsin, 2009; Ramsin and Paige, 2010).

## 3. Why the methodology should be model driven

### 3.1 The serverless abstraction gap

The preceding argument establishes the need for a serverless-aware method, but
does not yet establish that the method should be model driven. That second claim
depends on the kind of information a serverless project must control.

Much of a serverless system is declarative and relational. A function has a
runtime, resource limits, permissions, triggers, inputs, outputs, failure
behaviour, and dependencies. It participates in an event flow or workflow and
uses managed data and integration services. Business goals and quality
requirements must eventually be realized through provider-specific resources
and configuration. The relevant design is therefore scattered if it exists
only in source files, infrastructure templates, console settings, policy
documents, and developers' knowledge. This fragmentation makes it difficult to
answer basic questions: which requirement led to a function; which event can
invoke it; which data it may access; what happens after a retry; which resource
implements a platform-independent component; and what must change if a provider
construct changes.

This is an abstraction and traceability problem before it is a code-generation
problem. Schmidt argues that MDE is valuable where general-purpose programming
languages expose too much platform complexity and express domain concepts
poorly (Schmidt, 2006). Selic similarly places abstraction and automation at the
center of model-driven development, while emphasizing that useful models must
remain understandable, accurate, predictive, and sufficiently inexpensive to
construct (Selic, 2003). Serverless systems fit this problem profile: their
business behaviour is meaningful at a higher level than provider resource
syntax, but their deployability depends on precise platform details.

### 3.2 How MDE responds to the serverless situation

A model-driven method can establish a controlled path between those levels.
The contribution is not simply a diagram drawn before coding. Models become
primary engineering artifacts with defined abstract syntax, constraints, and
transformation semantics.

| Serverless engineering problem                                                                 | Model-driven mechanism                                                                                                                               | Expected methodological benefit                                                                                                              |
| ---------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Business intent is obscured by cloud configuration.                                            | A computation-independent model represents actors, goals, capabilities, processes, and information without committing to a provider.                 | Requirements remain visible and traceable after technical decisions are made.                                                                |
| Event, function, workflow, state, and contract decisions are distributed across artifacts.     | A domain-specific platform-independent model gives these concepts explicit types and relationships.                                                  | Architects and developers share a coherent system view; omissions and incompatible connections can be detected earlier.                      |
| Providers use different resource types, workflow languages, limits, and deployment formats.    | Model-to-model transformation refines a platform-independent design into a platform-specific model.                                                  | Provider knowledge is localized in mappings and PSM concepts instead of being mixed throughout the problem model.                            |
| Infrastructure and configuration are repetitive but exacting.                                  | Model-to-text generation produces code skeletons, infrastructure as code, policies, configuration, and pipeline artifacts from an authoritative PSM. | Repeated mappings are applied consistently and can be tested once in the generator rather than reimplemented manually in each component.     |
| Configuration errors and architectural inconsistencies are expensive to find after deployment. | Metamodel conformance and domain constraints check models before transformation and generation.                                                      | Defects can be reported in terms of domain concepts and at an earlier lifecycle point.                                                       |
| Changes cross requirements, design, deployment, and operation.                                 | Trace links relate requirements, model elements, transformation results, generated artifacts, tests, and telemetry.                                  | Impact analysis and change review become more systematic, while operational evidence can inform the next model revision.                     |
| Lock-in is difficult to see and expensive to reverse.                                          | Provider-neutral concepts are separated from explicit provider commitments in the PSM.                                                               | Portability limits become visible, alternative mappings can be added, and provider-specific dependencies can be governed rather than hidden. |

Research on MDE gives qualified support to these mechanisms. In three industrial
cases, Mohagheghi et al. found particular value in multiple abstraction levels,
domain-specific models that improved communication, simulation and testing,
analysis, and reuse across products. The same cases also found that tool
integration, transformations, usability, and management of large models
required substantial effort (Mohagheghi et al., 2013). This balance matters:
MDE provides leverage when recurring domain knowledge can be encoded and reused,
but a metamodel or generator is an investment, not a free by-product.

Serverless-specific research shows that the proposed mechanisms are technically
plausible. RADON combines serverless-oriented models, TOSCA-based topology and
orchestration, model-to-text transformation, quality assurance, CI/CD, and
runtime feedback in a model-driven DevOps framework. Its research agenda
explicitly connects models with function dependencies, event pipelines,
performance, cost, security, privacy, deployment, and lifecycle management
(Casale et al., 2020). Yussupov et al. demonstrate a more focused instance: a
technology-agnostic BPMN model of a function orchestration is transformed into
provider-specific workflow models, combined with a TOSCA deployment model, and
then enacted. Their work shows how model transformations can isolate proprietary
workflow and deployment languages, while also acknowledging that semantically
equivalent constructs do not always exist on every platform (Yussupov et al.,
2022).

These approaches establish feasibility, but they do not by themselves provide
the complete methodology sought in this thesis. A review of model-driven
approaches for serverless development found useful support for design and
deployment, alongside recurring omissions in early suitability analysis,
provider selection, cost and risk reasoning, project management, traceability,
deployment strategy, cold-start treatment, and operational feedback (Eidi and
Ramsin, 2026). This result mirrors an earlier observation about Model-Driven
Architecture: modeling levels and transformation principles are not a complete
software process. They need to be placed inside a lifecycle that covers
requirements, planning, construction, testing, deployment, maintenance, and
management (Asadi, Esfahani, and Ramsin, 2010).

### 3.3 A bounded justification for the model-driven choice

The second argument can therefore be stated as follows:

1. Serverless development repeatedly requires teams to relate domain intent,
   event-driven architecture, managed-service composition, quality constraints,
   and provider-specific realization.
2. These relations are structured, cross-cutting, and sufficiently repetitive
   to be represented through domain-specific metamodels and reusable
   transformations.
3. MDE offers explicit abstraction levels, domain-specific notation,
   constraint checking, transformation, generation, and traceability—the same
   mechanisms needed to control those relations.
4. Existing serverless MDE work demonstrates the feasibility of modeling and
   transforming event, orchestration, topology, and deployment information,
   while its gaps show why these facilities must be embedded in a complete
   development process.

On these premises, a model-driven serverless methodology is justified not
because modeling is inherently superior to programming, but because it moves
recurring serverless decisions into explicit, analyzable, and transformable
artifacts. Its value should be greatest where systems contain many interacting
functions and services, where consistency and traceability matter, where a
family of applications can reuse transformations, or where provider evolution
would otherwise require repeated manual work. A very small, short-lived
function may not recover the cost of a rich modeling approach. This is precisely
why the method must remain situational and tailorable.

## 4. Implication for the thesis

The two arguments together establish the rationale for MODRISS. Serverless is
not treated merely as the final deployment target of an otherwise conventional
process. It is treated as a development situation that changes technical,
organizational, operational, managerial, and business conditions. The process
part of MODRISS must therefore guide the full lifecycle: serverless suitability,
requirements, analysis, architecture, construction, verification, release,
operation, change, and retirement, together with project, risk, quality, and
configuration management.

The model-driven part addresses a complementary need. MODRISS separates
computation-independent intent, platform-independent serverless design, and
platform-specific realization through CIM, PIM, and PSM DSMLs. Ecore defines
their abstract syntax; EVL expresses semantic constraints for explicit model-
validation workflows; ETL realizes model-to-model refinement; and EGL/EGX
generates implementation and deployment artifacts. These facilities are most
useful when governed by the lifecycle rather than presented as an isolated
modeling recipe. The process establishes when a model is needed, who is
responsible for it, which evidence permits refinement or release, how generated
and handwritten artifacts remain aligned, and how operational learning returns
to requirements and design.

Accordingly, the primary thesis can be expressed with appropriate restraint:
**serverless adoption creates a recurring cluster of situational factors that
requires an explicitly tailored development method; because many of the
resulting concerns involve abstraction levels, structured relationships,
cross-artifact consistency, and repeatable realization, that method can benefit
materially from a model-driven foundation.** The effectiveness and economy of
the resulting methodology remain empirical questions and should be evaluated
through representative project enactments rather than inferred from its design
alone.

## References

Asadi, M., Esfahani, N., and Ramsin, R. (2010). “Process Patterns for
MDA-Based Software Development.” In _Proceedings of the 8th ACIS International
Conference on Software Engineering Research, Management and Applications_,
pp. 190–197. <https://doi.org/10.1109/SERA.2010.32>.

Asadi, M., and Ramsin, R. (2009). “Patterns of Situational Method Engineering.”
In _Software Engineering Research, Management and Applications 2009_, Studies
in Computational Intelligence, vol. 253, pp. 277–291. Springer.
<https://doi.org/10.1007/978-3-642-05441-9_24>.

Casale, G., Artač, M., van den Heuvel, W.-J., van Hoorn, A., Jakovits, P.,
Leymann, F., Long, M., Papanikolaou, V., Presenza, D., Russo, A., Srirama,
S. N., Tamburri, D. A., Wurster, M., and Zhu, L. (2020). “RADON: Rational
Decomposition and Orchestration for Serverless Computing.” _SICS
Software-Intensive Cyber-Physical Systems_, 35, 77–87.
<https://doi.org/10.1007/s00450-019-00413-w>.

Clarke, P., and O'Connor, R. V. (2012). “The Situational Factors That Affect
the Software Development Process: Towards a Comprehensive Reference
Framework.” _Information and Software Technology_, 54(5), 433–447.
<https://doi.org/10.1016/j.infsof.2011.12.003>.

Eidi, M., and Ramsin, R. (2026). “Model-Driven Approaches for Serverless
Software Development: Evaluation and Future Directions.” In _Proceedings of
the 14th International Conference on Model-Based Software and Systems
Engineering_, pp. 560–567. <https://doi.org/10.5220/0014634200004058>.

Eskandani, N., and Salvaneschi, G. (2023). “The Uphill Journey of FaaS in the
Open-Source Community.” _Journal of Systems and Software_, 198, 111589.
<https://doi.org/10.1016/j.jss.2022.111589>.

Leitner, P., Wittern, E., Spillner, J., and Hummer, W. (2019). “A Mixed-Method
Empirical Study of Function-as-a-Service Software Development in Industrial
Practice.” _Journal of Systems and Software_, 149, 340–359.
<https://doi.org/10.1016/j.jss.2018.12.013>.

Mohagheghi, P., Gilani, W., Stefanescu, A., Fernandez, M. A., Nordmoen, B.,
and Fritzsche, M. (2013). “Where Does Model-Driven Engineering Help?
Experiences from Three Industrial Cases.” _Software and Systems Modeling_,
12, 619–639. <https://doi.org/10.1007/s10270-011-0219-7>.

Ramsin, R., and Paige, R. F. (2010). “Iterative Criteria-Based Approach to
Engineering the Requirements of Software Development Methodologies.” _IET
Software_, 4(2), 91–104. <https://doi.org/10.1049/iet-sen.2009.0032>.

Schmidt, D. C. (2006). “Model-Driven Engineering.” _Computer_, 39(2), 25–31.
<https://doi.org/10.1109/MC.2006.58>.

Selic, B. (2003). “The Pragmatics of Model-Driven Development.” _IEEE
Software_, 20(5), 19–25. <https://doi.org/10.1109/MS.2003.1231146>.

Wen, J., Chen, Z., Liu, Y., Lou, Y., Ma, Y., Huang, G., Jin, X., and Liu, X.
(2021). “An Empirical Study on Challenges of Application Development in
Serverless Computing.” In _Proceedings of the 29th ACM Joint European Software
Engineering Conference and Symposium on the Foundations of Software
Engineering_, pp. 416–428. <https://doi.org/10.1145/3468264.3468558>.

Yussupov, V., Soldani, J., Breitenbücher, U., and Leymann, F. (2022).
“Standards-Based Modeling and Deployment of Serverless Function Orchestrations
Using BPMN and TOSCA.” _Software: Practice and Experience_, 52(6), 1454–1495.
<https://doi.org/10.1002/spe.3073>.
