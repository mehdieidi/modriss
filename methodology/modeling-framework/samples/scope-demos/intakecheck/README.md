# IntakeCheck demonstration

Follow the versioned object upload notification into ValidateCsv and inspect metadata, authorized result retrieval and immutable input identity.

Source: `.idea/scope/06-documents-and-file-processing/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](intakecheck.cim.xmi)
- [PIM model](intakecheck.pim.xmi)
- [PSM model](intakecheck.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Inputs are private CSV v1 with sku,title,priceCents; limit 1 MiB and 1000 rows.
- Validate required headers, unique SKU, nonblank title and positive integer price; reject the whole invalid catalogue with line-specific errors.
- Bind each result to immutable object version/hash; notification replay has one result.
- Supplier ownership gates upload/status/download; invalid content differs from retriable technical failure.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
