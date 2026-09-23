# Amazon API Gateway resources

API Gateway classes refine provider-independent APIs into HTTP, REST, and WebSocket resources, routes, integrations, stages, authorizers, and usage controls.

Source: `mde/metamodels/psm/awspsm-api.emf`.

## `ApiGatewayApi`

The common AWS API Gateway resource abstraction. It gathers routes, stages, integrations, authorizers, access logging, policies, and provider-specific API settings.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute           | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                    | Accepted values and example                                                                                                                                            |
| ------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `apiName`           | `String` [1]          | The api name that identifies this api gateway api in the model and its generated AWS configuration. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                    | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ApiGatewayApiExample`.                                               |
| `descriptionText`   | `String` [1]          | The provider-facing descriptive text emitted for this resource. It gives an operator context in the generated template and console without carrying runtime behavior. Within `ApiGatewayApi`, it applies to this specific element and its role in the surrounding model.                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Orders processing`.                                                  |
| `accessLogsEnabled` | `Boolean` [1]         | Whether access logs is enabled for this api gateway api. The flag turns an operational choice into explicit model data.                                                                                                                                                                                                                                                               | Either `true` or `false`. Example: `true`.                                                                                                                             |
| `tracingEnabled`    | `Boolean` [1]         | Whether tracing is enabled for this api gateway api. The flag turns an operational choice into explicit model data. Semantic validation: `ProductionApiShouldHaveMetricsAndTracing` (production api should have metrics and tracing) in `mde/validation/psm/rules/api.evl` the related value or object must be explicitly provided.                                                   | Either `true` or `false`. Example: `true`.                                                                                                                             |
| `metricsEnabled`    | `Boolean` [1]         | Whether metrics is enabled for this api gateway api. The flag turns an operational choice into explicit model data.                                                                                                                                                                                                                                                                   | Either `true` or `false`. Example: `true`.                                                                                                                             |
| `corsEnabled`       | `Boolean` [1]         | Whether cors is enabled for this api gateway api. The flag turns an operational choice into explicit model data.                                                                                                                                                                                                                                                                      | Either `true` or `false`. Example: `true`.                                                                                                                             |
| `definitionUri`     | `String` [1]          | The location of an external OpenAPI definition used to describe the API. It supports designs in which the contract is maintained as a separate artifact.                                                                                                                                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `https://example.com/orders`.                                         |
| `definitionBody`    | `String` [1]          | An inline OpenAPI definition for the API. This form keeps the contract inside the model when an external definition file is unnecessary. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/templates/contracts/openapi.egl`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `A confirmed order is one accepted for fulfillment by the business.`. |
| `openApiVersion`    | `String` [1]          | The OpenAPI version expected for the supplied definition, allowing generators and reviewers to interpret its syntax correctly.                                                                                                                                                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `1.0`.                                                                |
| `apiPolicyJson`     | `String` [1]          | The serialized representation of api policy on this api gateway api. It carries provider-specific structure that is intentionally retained as document content. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                                   |

### Relationships

| Relationship                                      | Kind and multiplicity            | Meaning in the model                                                                                                                                                                                                                                                       |
| ------------------------------------------------- | -------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `cors` → `CorsConfiguration`                      | containment, [?]                 | The owned cross-origin policy controlling browser access to this endpoint.                                                                                                                                                                                                 |
| `tracingConfig` → `ApiGatewayTracingConfig`       | containment, [?]                 | The `tracingConfig` containment on `ApiGatewayApi` attaches the configuration record represented by tracing config. The `ApiGatewayTracingConfig` objects are owned by `ApiGatewayApi` and remain part of its model subtree.                                               |
| `accessLogSetting` → `ApiGatewayAccessLogSetting` | containment, [?]                 | The owned destination and format for access records.                                                                                                                                                                                                                       |
| `apiPolicy` → `IamPolicyDocument`                 | containment, [?]                 | The `apiPolicy` containment on `ApiGatewayApi` attaches the access or governance decision represented by api policy. The `IamPolicyDocument` objects are owned by `ApiGatewayApi` and remain part of its model subtree.                                                    |
| `accessLogGroup` → `CloudWatchLogGroup`           | reference, [?]                   | The CloudWatch log group receiving access records from this API or stage.                                                                                                                                                                                                  |
| `routes` → `ApiGatewayRoute`                      | containment, [*]; opposite `api` | The `routes` containment on `ApiGatewayApi` owns the addressable operations exposed by the API. The `ApiGatewayRoute` objects are owned by `ApiGatewayApi` and remain part of its model subtree. Its opposite `api` exposes the same connection from the target side.      |
| `stages` → `ApiGatewayStage`                      | containment, [*]; opposite `api` | The `stages` containment on `ApiGatewayApi` keeps the provider deployment stages under the PSM root. The `ApiGatewayStage` objects are owned by `ApiGatewayApi` and remain part of its model subtree. Its opposite `api` exposes the same connection from the target side. |
| `authorizers` → `ApiGatewayAuthorizer`            | containment, [*]; opposite `api` | Authorizers declared within this API.                                                                                                                                                                                                                                      |
| `apiDomainName` → `ApiGatewayDomainName`          | reference, [?]; opposite `api`   | The custom API Gateway domain assigned to this API.                                                                                                                                                                                                                        |
| `wafAssociation` → `WafWebAclAssociation`         | reference, [?]                   | The optional WAF web ACL attachment protecting this API.                                                                                                                                                                                                                   |

## `HttpApi`

An API Gateway HTTP API deployment model. It specializes the common API resource with the lighter HTTP API surface and its route and stage configuration.

Direct supertypes: `ApiGatewayApi`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                   | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                  | Accepted values and example                                                                                  |
| --------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `protocolType`              | `String` [1]          | The protocol family exposed by the HTTP API, recorded explicitly because API Gateway uses it when creating the provider resource. Transformation role: ETL rule `Api2HttpApi` in `mde/transformations/pim-to-awspsm/compute-api.etl` assigns or materializes this feature while refining `HttpApi`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `BUSINESS`. |
| `disableExecuteApiEndpoint` | `Boolean` [1]         | The boolean decision for disable execute api endpoint on this http api. It keeps an important design choice explicit for review and transformation.                                                                                                                                                 | Either `true` or `false`. Example: `false`.                                                                  |
| `minimumCompressionSize`    | `Integer` [1]         | The numeric value used for minimum compression size on this http api. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.                                                                                                                        | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.             |
| `failOnWarnings`            | `Boolean` [1]         | The boolean decision for fail on warnings on this http api. It keeps an important design choice explicit for review and transformation.                                                                                                                                                             | Either `true` or `false`. Example: `false`.                                                                  |

### Relationships

This class declares no direct relationships.

## `RestApi`

An API Gateway REST API deployment model. It provides the resource and method structure, models, authorizers, stages, usage controls, and integrations needed by REST APIs.

Direct supertypes: `ApiGatewayApi`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                 | Type and multiplicity        | What it captures and why it exists                                                                                                                                                                                                                                                                        | Accepted values and example                                                                      |
| ------------------------- | ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `minimumCompressionSize`  | `Integer` [1]                | The response-body size threshold, in bytes, above which API Gateway may apply compression. Transformation role: ETL rule `Api2RestApi` in `mde/transformations/pim-to-awspsm/compute-api.etl` assigns or materializes this feature while refining `RestApi`.                                              | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`. |
| `failOnWarnings`          | `Boolean` [1]                | The boolean decision for fail on warnings on this rest api. It keeps an important design choice explicit for review and transformation. Transformation role: ETL rule `Api2RestApi` in `mde/transformations/pim-to-awspsm/compute-api.etl` assigns or materializes this feature while refining `RestApi`. | Either `true` or `false`. Example: `false`.                                                      |
| `apiKeyRequiredByDefault` | `Boolean` [1]                | The boolean decision for api key required by default on this rest api. It keeps an important design choice explicit for review and transformation.                                                                                                                                                        | Either `true` or `false`. Example: `true`.                                                       |
| `endpointType`            | `ApiGatewayEndpointType` [1] | The controlled value used for endpoint type on this rest api. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.                                                                                                                                      | Exactly one of: `REGIONAL`, `EDGE`, `PRIVATE`. Example: `REGIONAL`.                              |

### Relationships

| Relationship                    | Kind and multiplicity            | Meaning in the model                                                                                                                                                                                                                                                    |
| ------------------------------- | -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `resources` → `RestApiResource` | containment, [*]; opposite `api` | The `resources` containment on `RestApi` keeps the AWS resources grouped under the deployment boundary. The `RestApiResource` objects are owned by `RestApi` and remain part of its model subtree. Its opposite `api` exposes the same connection from the target side. |

## `WebSocketApi`

An API Gateway WebSocket deployment model. It expresses connection, route, integration, and stage settings for a bidirectional API surface.

Direct supertypes: `ApiGatewayApi`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                  | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                     | Accepted values and example                                                                                             |
| -------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `routeSelectionExpression` | `String` [1]          | The expression used to determine route selection for this web socket api. It keeps executable or provider-interpreted logic visible to validation and generation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `$.status == 'READY'`. |

### Relationships

This class declares no direct relationships.

## `ApiGatewayRoute`

`ApiGatewayRoute` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway route. Its declaration gives the concept a precise home through request models, response models, route settings. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute               | Type and multiplicity             | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                              | Accepted values and example                                                                                                |
| ----------------------- | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `operationName`         | `String` [1]                      | The operation name that identifies this api gateway route in the model and its generated AWS configuration. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ApiGatewayRouteExample`. |
| `apiKeyRequired`        | `Boolean` [1]                     | Whether this api gateway route requires api key. The flag records an obligation that validation and generation can carry forward.                                                                                                                                                                                                                                               | Either `true` or `false`. Example: `true`.                                                                                 |
| `authorizationScopes`   | `String` [*]                      | Records boundary within which authorization scopes is interpreted for api gateway route. It keeps isolation and ownership decisions explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                     | A collection of values. Example: [`orders`, `orders-2`].                                                                   |
| `requestParametersJson` | `String` [1]                      | The serialized representation of request parameters on this api gateway route. It carries provider-specific structure that is intentionally retained as document content. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.       |
| `requestModelsJson`     | `String` [1]                      | The serialized representation of request models on this api gateway route. It carries provider-specific structure that is intentionally retained as document content.                                                                                                                                                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.       |
| `responseModelsJson`    | `String` [1]                      | The serialized representation of response models on this api gateway route. It carries provider-specific structure that is intentionally retained as document content.                                                                                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.       |
| `routeSettingsJson`     | `String` [1]                      | The serialized representation of route settings on this api gateway route. It carries provider-specific structure that is intentionally retained as document content.                                                                                                                                                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.       |
| `timeoutInMillis`       | `Integer` [1]                     | The numeric value used for timeout in millis on this api gateway route. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.                                                                                                                                                                                                  | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `30`.                          |
| `authorizationType`     | `ApiGatewayAuthorizationType` [1] | The authorization mechanism applied to requests for this route. Its value determines whether an authorizer is meaningful and which kind can be attached. Semantic validation: `ProtectedRouteHasRequiredAuthConfiguration` (protected route has required auth configuration) in `mde/validation/psm/rules/api.evl` the feature participates in a semantic validation condition. | Exactly one of: `NONE`, `AWS_IAM`, `JWT`, `COGNITO_USER_POOLS`, `CUSTOM_LAMBDA`, `API_KEY`. Example: `NONE`.               |

### Relationships

| Relationship                                 | Kind and multiplicity                        | Meaning in the model                                                                                                                                                                                                          |
| -------------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `requestModels` → `ApiGatewayRequestModel`   | containment, [*]                             | Request schemas used to describe or validate payloads accepted by the route.                                                                                                                                                  |
| `responseModels` → `ApiGatewayResponseModel` | containment, [*]                             | Schemas describing response bodies associated with the route.                                                                                                                                                                 |
| `routeSettings` → `ApiGatewayRouteSetting`   | containment, [?]                             | Per-route throttling, logging, tracing, and metrics settings.                                                                                                                                                                 |
| `api` → `ApiGatewayApi`                      | reference; read-only, [1]; opposite `routes` | The API that owns or is configured by this element.                                                                                                                                                                           |
| `integration` → `ApiGatewayIntegration`      | reference, [1]                               | The `integration` reference on `ApiGatewayRoute` identifies the integration that carries this route or event path. An `ApiGatewayIntegration` can remain independently owned and can participate in other parts of the model. |
| `authorizer` → `ApiGatewayAuthorizer`        | reference, [?]                               | The route authorizer responsible for evaluating the caller before integration.                                                                                                                                                |

## `HttpApiRoute`

A route declaration for an API Gateway HTTP API. It binds a method and path, or their provider route-key form, to the integration inherited from `ApiGatewayRoute`.

Direct supertypes: `ApiGatewayRoute`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute  | Type and multiplicity      | What it captures and why it exists                                                                                                                                                                                                                                                                                                                             | Accepted values and example                                                                                           |
| ---------- | -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `path`     | `String` [1]               | The URI path matched by this HTTP API route, including any path parameters. Semantic validation: `UniqueHttpRouteWithinApi` (unique http route within api) in `mde/validation/psm/rules/api.evl` the rule's diagnostic or remediation guidance refers to this feature.                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |
| `routeKey` | `String` [1]               | The API Gateway route key used for dispatch. It can preserve the provider form when it carries more information than `method` and `path` separately.                                                                                                                                                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`.           |
| `method`   | `ApiGatewayHttpMethod` [1] | The controlled value used for method on this http api route. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently. Semantic validation: `UniqueHttpRouteWithinApi` (unique http route within api) in `mde/validation/psm/rules/api.evl` the rule's diagnostic or remediation guidance refers to this feature. | Exactly one of: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, `HEAD`, `ANY`. Example: `GET`.                    |

### Relationships

This class declares no direct relationships.

## `RestApiRoute`

A path-and-method route in the REST API vocabulary. It records the client-visible path as well as the API Gateway resource path used during deployment.

Direct supertypes: `ApiGatewayRoute`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute             | Type and multiplicity      | What it captures and why it exists                                                                                                                                  | Accepted values and example                                                                                           |
| --------------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `path`                | `String` [1]               | The client-visible URI path handled by this REST route.                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |
| `restApiResourcePath` | `String` [1]               | The resource-tree path used to associate this route with an API Gateway REST resource.                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |
| `method`              | `ApiGatewayHttpMethod` [1] | The controlled value used for method on this rest api route. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently. | Exactly one of: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, `HEAD`, `ANY`. Example: `GET`.                    |

### Relationships

This class declares no direct relationships.

## `RestApiResource`

`RestApiResource` is a provider resource record in the AWS platform-specific model. It gives rest api resource a concrete deployment identity. Its declaration gives the concept a precise home through api, parent resource. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute  | Type and multiplicity | What it captures and why it exists                                                                                                   | Accepted values and example                                                                                           |
| ---------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------- |
| `pathPart` | `String` [1]          | The single path segment represented by this resource beneath its parent.                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |
| `fullPath` | `String` [1]          | The complete resolved path of the REST resource. It provides a stable view when the path is assembled from several parent resources. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |

### Relationships

| Relationship                         | Kind and multiplicity                           | Meaning in the model                                                                                                                                                                                          |
| ------------------------------------ | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `api` → `RestApi`                    | reference; read-only, [1]; opposite `resources` | The API that owns or is configured by this element.                                                                                                                                                           |
| `parentResource` → `RestApiResource` | reference, [?]                                  | The `parentResource` reference on `RestApiResource` identifies the source represented by parent resource. A `RestApiResource` can remain independently owned and can participate in other parts of the model. |

## `RestApiMethod`

The method resource attached to one `RestApiResource`. Its optional validator adds request checking to the integration and authorization inherited from `ApiGatewayRoute`.

Direct supertypes: `ApiGatewayRoute`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity      | What it captures and why it exists                                                                                                                                   | Accepted values and example                                                                        |
| --------- | -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `method`  | `ApiGatewayHttpMethod` [1] | The controlled value used for method on this rest api method. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently. | Exactly one of: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, `HEAD`, `ANY`. Example: `GET`. |

### Relationships

| Relationship                                      | Kind and multiplicity | Meaning in the model                                                                                                                                                                           |
| ------------------------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `resource` → `RestApiResource`                    | reference, [1]        | The `resource` reference on `RestApiMethod` identifies the source represented by resource. A `RestApiResource` can remain independently owned and can participate in other parts of the model. |
| `requestValidator` → `ApiGatewayRequestValidator` | reference, [?]        | The REST API request validator applied before this method reaches its integration.                                                                                                             |

## `WebSocketRoute`

A WebSocket dispatch route identified by its route key. The inherited integration specifies what handles matching connection or application messages.

Direct supertypes: `ApiGatewayRoute`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute  | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                        | Accepted values and example                                                                                 |
| ---------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `routeKey` | `String` [1]          | The WebSocket selection value handled by the route, such as `$connect`, `$disconnect`, `$default`, or an application action. Semantic validation: `UniqueWebSocketRouteKeyWithinApi` (unique web socket route key within api) in `mde/validation/psm/rules/api.evl` the rule's diagnostic or remediation guidance refers to this feature. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`. |

### Relationships

This class declares no direct relationships.

## `ApiGatewayRequestModel`

`ApiGatewayRequestModel` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway request model. Its declaration gives the concept a precise home through schema resource. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute     | Type and multiplicity | What it captures and why it exists                                                                                                                                    | Accepted values and example                                                                                                       |
| ------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `modelName`   | `String` [1]          | The model name that identifies this api gateway request model in the model and its generated AWS configuration.                                                       | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ApiGatewayRequestModelExample`. |
| `contentType` | `String` [1]          | The media type for which this request model supplies a schema.                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `BUSINESS`.                      |
| `schemaJson`  | `String` [1]          | The serialized representation of schema on this api gateway request model. It carries provider-specific structure that is intentionally retained as document content. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.              |

### Relationships

| Relationship                     | Kind and multiplicity | Meaning in the model                                                                                                                                                                                              |
| -------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `schemaResource` → `AwsResource` | reference, [?]        | The `schemaResource` reference on `ApiGatewayRequestModel` identifies the source represented by schema resource. An `AwsResource` can remain independently owned and can participate in other parts of the model. |

## `ApiGatewayResponseModel`

`ApiGatewayResponseModel` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway response model. Its declaration gives the concept a precise home through schema resource. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute     | Type and multiplicity | What it captures and why it exists                                                                                                                                     | Accepted values and example                                                                                          |
| ------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `statusCode`  | `String` [1]          | The response status associated with this response model.                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ORDER_NOT_FOUND`.  |
| `contentType` | `String` [1]          | The media type whose response body is described by this model.                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `BUSINESS`.         |
| `schemaJson`  | `String` [1]          | The serialized representation of schema on this api gateway response model. It carries provider-specific structure that is intentionally retained as document content. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`. |

### Relationships

| Relationship                     | Kind and multiplicity | Meaning in the model                                                                                                                                                                                               |
| -------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `schemaResource` → `AwsResource` | reference, [?]        | The `schemaResource` reference on `ApiGatewayResponseModel` identifies the source represented by schema resource. An `AwsResource` can remain independently owned and can participate in other parts of the model. |

## `ApiGatewayRequestValidator`

`ApiGatewayRequestValidator` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway request validator. Its declaration gives the concept a precise home through api. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                   | Type and multiplicity | What it captures and why it exists                                                                                                                                      | Accepted values and example                                                                                                           |
| --------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `validatorName`             | `String` [1]          | The validator name that identifies this api gateway request validator in the model and its generated AWS configuration.                                                 | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ApiGatewayRequestValidatorExample`. |
| `validateRequestBody`       | `Boolean` [1]         | The boolean decision for validate request body on this api gateway request validator. It keeps an important design choice explicit for review and transformation.       | Either `true` or `false`. Example: `false`.                                                                                           |
| `validateRequestParameters` | `Boolean` [1]         | The boolean decision for validate request parameters on this api gateway request validator. It keeps an important design choice explicit for review and transformation. | Either `true` or `false`. Example: `false`.                                                                                           |

### Relationships

| Relationship      | Kind and multiplicity | Meaning in the model                                |
| ----------------- | --------------------- | --------------------------------------------------- |
| `api` → `RestApi` | reference, [1]        | The API that owns or is configured by this element. |

## `ApiGatewayRouteSetting`

`ApiGatewayRouteSetting` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway route setting. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                | Type and multiplicity | What it captures and why it exists                                                                                                                                                          | Accepted values and example                                                                              |
| ------------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `detailedMetricsEnabled` | `Boolean` [1]         | Whether detailed metrics is enabled for this api gateway route setting. The flag turns an operational choice into explicit model data.                                                      | Either `true` or `false`. Example: `true`.                                                               |
| `dataTraceEnabled`       | `Boolean` [1]         | Whether data trace is enabled for this api gateway route setting. The flag turns an operational choice into explicit model data.                                                            | Either `true` or `false`. Example: `true`.                                                               |
| `loggingLevel`           | `String` [1]          | The execution-log level applied to this route.                                                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `INFO`. |
| `throttlingBurstLimit`   | `Integer` [1]         | The numeric value used for throttling burst limit on this api gateway route setting. Keeping the quantity in the model lets validation and generation apply the same boundary consistently. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.         |
| `throttlingRateLimit`    | `Double` [1]          | The numeric value used for throttling rate limit on this api gateway route setting. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.  | A numeric `Double` value; use the unit or boundary documented for this attribute. Example: `1`.          |

### Relationships

This class declares no direct relationships.

## `ApiGatewayAccessLogSetting`

`ApiGatewayAccessLogSetting` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway access log setting. Its declaration gives the concept a precise home through destination log group. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                          | Accepted values and example                                                                                                                               |
| ---------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `format`         | `String` [1]          | The serialization format expected for this structured document. The value tells consumers how to parse or emit the content. Within `ApiGatewayAccessLogSetting`, it applies to this specific element and its role in the surrounding model. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Api Gateway Access Log Setting Format`.                 |
| `destinationArn` | `String` [1]          | The ARN to which API Gateway writes access-log records. The `destinationLogGroup` reference may provide the same destination structurally.                                                                                                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |

### Relationships

| Relationship                                 | Kind and multiplicity | Meaning in the model                                                               |
| -------------------------------------------- | --------------------- | ---------------------------------------------------------------------------------- |
| `destinationLogGroup` → `CloudWatchLogGroup` | reference, [?]        | The modelled CloudWatch log group corresponding to the access-log destination ARN. |

## `ApiGatewayIntegration`

`ApiGatewayIntegration` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway integration. Its declaration gives the concept a precise home through response parameters, request templates, lambda target. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                | Type and multiplicity           | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Accepted values and example                                                                                                                               |
| ------------------------ | ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `integrationUri`         | `String` [1]                    | The provider integration endpoint invoked by API Gateway. Its interpretation depends on the integration type and may identify Lambda, Step Functions, an AWS service, or an HTTP endpoint.                                                                                                                                                                                                                                                                                                                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `https://example.com/orders`.                            |
| `integrationMethod`      | `String` [1]                    | The HTTP method API Gateway uses when invoking the integration endpoint; it can differ from the client-facing route method. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                                                                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Api Gateway Integration Integration Method`.            |
| `payloadFormatVersion`   | `String` [1]                    | Records serialization/content format for payload format version for api gateway integration. It keeps compatibility and contract evolution explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`.                                                                                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `1.0`.                                                   |
| `credentialsArn`         | `String` [1]                    | The ARN of the IAM role assumed by API Gateway for the integration when credentials are required.                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `timeoutInMillis`        | `Integer` [1]                   | The numeric value used for timeout in millis on this api gateway integration. Keeping the quantity in the model lets validation and generation apply the same boundary consistently.                                                                                                                                                                                                                                                                                                                                                                                 | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `30`.                                                         |
| `passthroughBehavior`    | `String` [1]                    | The request-template fallback behavior used when no mapping template matches the incoming content type.                                                                                                                                                                                                                                                                                                                                                                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Api Gateway Integration Passthrough Behavior`.          |
| `requestTemplatesJson`   | `String` [1]                    | The serialized representation of request templates on this api gateway integration. It carries provider-specific structure that is intentionally retained as document content. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                                                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                      |
| `responseParametersJson` | `String` [1]                    | Records serialized JSON representation of response parameters for api gateway integration. It keeps preserving structured provider or contract detail explicit during review and transformation, so later steps do not have to infer it. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                        | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                                      |
| `integrationType`        | `ApiGatewayIntegrationType` [1] | The API Gateway integration strategy. It controls how the integration URI, request templates, credentials, and response mapping are interpreted. Semantic validation: `IntegrationHasSingleTarget` (integration has single target) in `mde/validation/psm/rules/api.evl` the feature participates in a semantic validation condition. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/contracts.eol`. | Exactly one of: `AWS_PROXY`, `AWS`, `HTTP_PROXY`, `HTTP`, `MOCK`. Example: `AWS_PROXY`.                                                                   |

### Relationships

| Relationship                                                    | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                                |
| --------------------------------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `responseParameters` → `ApiGatewayIntegrationResponseParameter` | containment, [*]      | Header or parameter mappings produced by the API integration.                                                                                                                                                                       |
| `requestTemplates` → `ApiGatewayIntegrationRequestTemplate`     | containment, [*]      | Media-type-specific templates that transform incoming request payloads for the integration.                                                                                                                                         |
| `lambdaTarget` → `AwsLambdaFunction`                            | reference, [?]        | The `lambdaTarget` reference on `ApiGatewayIntegration` identifies the destination represented by lambda target. An `AwsLambdaFunction` can remain independently owned and can participate in other parts of the model.             |
| `stateMachineTarget` → `StepFunctionStateMachine`               | reference, [?]        | The `stateMachineTarget` reference on `ApiGatewayIntegration` identifies the state machine receiving this integration. A `StepFunctionStateMachine` can remain independently owned and can participate in other parts of the model. |
| `credentialsRole` → `IamRole`                                   | reference, [?]        | The IAM role API Gateway assumes while invoking this integration.                                                                                                                                                                   |

## `ApiGatewayIntegrationRequestTemplate`

`ApiGatewayIntegrationRequestTemplate` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway integration request template. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute     | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                              | Accepted values and example                                                                                                                           |
| ------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `contentType` | `String` [1]          | The request media type to which this mapping template applies.                                                                                                                                                                                                                                                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `BUSINESS`.                                          |
| `template`    | `String` [1]          | The mapping-template body applied to requests of the associated content type. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx`. The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Api Gateway Integration Request Template Template`. |

### Relationships

This class declares no direct relationships.

## `ApiGatewayIntegrationResponseParameter`

`ApiGatewayIntegrationResponseParameter` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway integration response parameter. Its declaration gives the concept a precise home through value. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TraceableElement`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute       | Type and multiplicity | What it captures and why it exists                                                                                                   | Accepted values and example                                                                                                                       |
| --------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `parameterName` | `String` [1]          | The parameter name that identifies this api gateway integration response parameter in the model and its generated AWS configuration. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ApiGatewayIntegrationResponseParameterExample`. |

### Relationships

| Relationship                | Kind and multiplicity | Meaning in the model                                                                                                         |
| --------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `value` → `ValueExpression` | containment, [1]      | The structured value expression supplying this setting; it may be literal, parameter-backed, resource-derived, or composite. |

## `ApiGatewayStage`

`ApiGatewayStage` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway stage. Its declaration gives the concept a precise home through access log setting, default route settings, api. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`, `WafAssociableResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute              | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Accepted values and example                                                                                                             |
| ---------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `stageName`            | `String` [1]          | The stage name that identifies this api gateway stage in the model and its generated AWS configuration. Semantic validation: `AccessLogStageRequiresGroupAndFormat` (access log stage requires group and format) in `mde/validation/psm/rules/api.evl` the rule's diagnostic or remediation guidance refers to this feature. `ProductionStageShouldThrottle` (production stage should throttle) in `mde/validation/psm/rules/api.evl` the rule's diagnostic or remediation guidance refers to this feature. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ApiGatewayStageExample`.              |
| `autoDeploy`           | `Boolean` [1]         | The boolean decision for auto deploy on this api gateway stage. It keeps an important design choice explicit for review and transformation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                                            | Either `true` or `false`. Example: `false`.                                                                                             |
| `accessLogEnabled`     | `Boolean` [1]         | Whether access log is enabled for this api gateway stage. The flag turns an operational choice into explicit model data.                                                                                                                                                                                                                                                                                                                                                                                    | Either `true` or `false`. Example: `true`.                                                                                              |
| `accessLogFormat`      | `String` [1]          | The record layout written for each API Gateway access-log entry. Semantic validation: `AccessLogStageRequiresGroupAndFormat` (access log stage requires group and format) in `mde/validation/psm/rules/api.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                               | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Api Gateway Stage Access Log Format`. |
| `metricsEnabled`       | `Boolean` [1]         | Whether metrics is enabled for this api gateway stage. The flag turns an operational choice into explicit model data.                                                                                                                                                                                                                                                                                                                                                                                       | Either `true` or `false`. Example: `true`.                                                                                              |
| `dataTraceEnabled`     | `Boolean` [1]         | Whether data trace is enabled for this api gateway stage. The flag turns an operational choice into explicit model data.                                                                                                                                                                                                                                                                                                                                                                                    | Either `true` or `false`. Example: `true`.                                                                                              |
| `loggingLevel`         | `String` [1]          | The default execution-log level for routes deployed in this stage.                                                                                                                                                                                                                                                                                                                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `INFO`.                                |
| `throttlingBurstLimit` | `Integer` [1]         | Stores the throttling burst limit value on the api gateway stage. The field records an operational boundary explicitly instead of leaving it to provider defaults. Semantic validation: `ProductionStageShouldThrottle` (production stage should throttle) in `mde/validation/psm/rules/api.evl` the related value or object must be explicitly provided.                                                                                                                                                   | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                        |
| `throttlingRateLimit`  | `Double` [1]          | Stores the throttling rate limit value on the api gateway stage. The field records an operational boundary explicitly instead of leaving it to provider defaults. Semantic validation: `ProductionStageShouldThrottle` (production stage should throttle) in `mde/validation/psm/rules/api.evl` the related value or object must be explicitly provided.                                                                                                                                                    | A numeric `Double` value; use the unit or boundary documented for this attribute. Example: `1`.                                         |
| `variablesJson`        | `String` [1]          | The serialized representation of variables on this api gateway stage. It carries provider-specific structure that is intentionally retained as document content.                                                                                                                                                                                                                                                                                                                                            | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `{"enabled":true}`.                    |

### Relationships

| Relationship                                      | Kind and multiplicity                        | Meaning in the model                                                             |
| ------------------------------------------------- | -------------------------------------------- | -------------------------------------------------------------------------------- |
| `accessLogSetting` → `ApiGatewayAccessLogSetting` | containment, [?]                             | The owned destination and format for access records.                             |
| `defaultRouteSettings` → `ApiGatewayRouteSetting` | containment, [?]                             | Settings inherited by routes that do not provide their own stage-level override. |
| `api` → `ApiGatewayApi`                           | reference; read-only, [1]; opposite `stages` | The API that owns or is configured by this element.                              |
| `accessLogGroup` → `CloudWatchLogGroup`           | reference, [?]                               | The CloudWatch log group receiving access records from this API or stage.        |

## `HttpApiStage`

`HttpApiStage` is a deployment-stage record in the AWS platform-specific model. It gives http api stage a provider-facing boundary. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `ApiGatewayStage`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `RestApiStage`

`RestApiStage` is a deployment-stage record in the AWS platform-specific model. It gives rest api stage a provider-facing boundary. Its declaration gives the concept a precise home through deployment. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `ApiGatewayStage`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                          | Kind and multiplicity | Meaning in the model                                 |
| ------------------------------------- | --------------------- | ---------------------------------------------------- |
| `deployment` → `ApiGatewayDeployment` | reference, [?]        | The REST API deployment released through this stage. |

## `WebSocketStage`

`WebSocketStage` is a deployment-stage record in the AWS platform-specific model. It gives web socket stage a provider-facing boundary. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `ApiGatewayStage`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `ApiGatewayAuthorizer`

`ApiGatewayAuthorizer` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway authorizer. Its declaration gives the concept a precise home through api. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                      | Type and multiplicity             | What it captures and why it exists                                                                                                                                                                 | Accepted values and example                                                                                                                |
| ------------------------------ | --------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `authorizerName`               | `String` [1]                      | The authorizer name that identifies this api gateway authorizer in the model and its generated AWS configuration.                                                                                  | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ApiGatewayAuthorizerExample`.            |
| `identitySource`               | `String` [1]                      | The request expression or expressions from which API Gateway obtains the caller identity, token, or authorization input.                                                                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Api Gateway Authorizer Identity Source`. |
| `authorizerResultTtlInSeconds` | `Integer` [1]                     | The numeric value used for authorizer result ttl in seconds on this api gateway authorizer. Keeping the quantity in the model lets validation and generation apply the same boundary consistently. | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                           |
| `authorizationType`            | `ApiGatewayAuthorizationType` [1] | The controlled value used for authorization type on this api gateway authorizer. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.            | Exactly one of: `NONE`, `AWS_IAM`, `JWT`, `COGNITO_USER_POOLS`, `CUSTOM_LAMBDA`, `API_KEY`. Example: `NONE`.                               |

### Relationships

| Relationship            | Kind and multiplicity                             | Meaning in the model                                |
| ----------------------- | ------------------------------------------------- | --------------------------------------------------- |
| `api` → `ApiGatewayApi` | reference; read-only, [1]; opposite `authorizers` | The API that owns or is configured by this element. |

## `JwtAuthorizer`

An HTTP API authorizer that validates JSON Web Tokens against an issuer and one or more accepted audiences.

Direct supertypes: `ApiGatewayAuthorizer`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute  | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                                               | Accepted values and example                                                                                               |
| ---------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------- |
| `issuer`   | `String` [1]          | The flag stating whether issuer applies to this jwt authorizer. It turns an architectural or governance decision into explicit model data. Semantic validation: `JwtAuthorizerHasIssuerAndAudience` (jwt authorizer has issuer and audience) in `mde/validation/psm/rules/api.evl` the value must be present and non-blank. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Jwt Authorizer Issuer`. |
| `audience` | `String` [*]          | The accepted JWT audience values used to reject tokens issued for a different client or service.                                                                                                                                                                                                                                                                                                                                                 | A collection of values. Example: [`Jwt Authorizer Audience`, `Jwt Authorizer Audience-2`].                                |

### Relationships

This class declares no direct relationships.

## `CognitoAuthorizer`

`CognitoAuthorizer` is a Cognito deployment record in the AWS platform-specific model. It carries the identity settings for cognito authorizer. Its declaration gives the concept a precise home through user pool, clients. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `ApiGatewayAuthorizer`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

| Relationship                        | Kind and multiplicity | Meaning in the model                                                                          |
| ----------------------------------- | --------------------- | --------------------------------------------------------------------------------------------- |
| `userPool` → `CognitoUserPool`      | reference, [1]        | The Cognito user pool to which this client, group, domain, or identity configuration belongs. |
| `clients` → `CognitoUserPoolClient` | reference, [*]        | The application clients registered against this user pool.                                    |

## `LambdaAuthorizer`

`LambdaAuthorizer` is a Lambda deployment record in the AWS platform-specific model. It carries the provider settings for lambda authorizer. Its declaration gives the concept a precise home through function. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `ApiGatewayAuthorizer`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute                        | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                           | Accepted values and example                                                                             |
| -------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `authorizerPayloadFormatVersion` | `String` [1]          | The payload contract version used between API Gateway and the Lambda authorizer.                                                                                                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `1.0`. |
| `enableSimpleResponses`          | `Boolean` [1]         | The boolean decision for enable simple responses on this lambda authorizer. It keeps an important design choice explicit for review and transformation. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | Either `true` or `false`. Example: `false`.                                                             |

### Relationships

| Relationship                     | Kind and multiplicity | Meaning in the model                                      |
| -------------------------------- | --------------------- | --------------------------------------------------------- |
| `function` → `AwsLambdaFunction` | reference, [1]        | The Lambda function that participates in this connection. |

## `ApiGatewayDomainName`

`ApiGatewayDomainName` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway domain name. Its declaration gives the concept a precise home through api, mappings. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute        | Type and multiplicity        | What it captures and why it exists                                                                                                                                                                                                                                                                                                                                                                                   | Accepted values and example                                                                                                                               |
| ---------------- | ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `domainName`     | `String` [1]                 | The domain name that identifies this api gateway domain name in the model and its generated AWS configuration. Semantic validation: `DomainHasCertificate` (domain has certificate) in `mde/validation/psm/rules/api.evl` the rule's diagnostic or remediation guidance refers to this feature. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orders.example.com`.                                    |
| `certificateArn` | `String` [1]                 | The ACM certificate ARN used to terminate TLS for the custom domain. Semantic validation: `DomainHasCertificate` (domain has certificate) in `mde/validation/psm/rules/api.evl` the value must be present and non-blank.                                                                                                                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |
| `securityPolicy` | `String` [1]                 | The TLS security policy enforced by the API Gateway custom domain. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                                                                                                                                              | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `STANDARD`.                                              |
| `endpointType`   | `ApiGatewayEndpointType` [1] | The controlled value used for endpoint type on this api gateway domain name. It keeps the distinction explicit in the abstract syntax so later rules can interpret it consistently.                                                                                                                                                                                                                                  | Exactly one of: `REGIONAL`, `EDGE`, `PRIVATE`. Example: `REGIONAL`.                                                                                       |

### Relationships

| Relationship                             | Kind and multiplicity                    | Meaning in the model                                                |
| ---------------------------------------- | ---------------------------------------- | ------------------------------------------------------------------- |
| `api` → `ApiGatewayApi`                  | reference, [?]; opposite `apiDomainName` | The API that owns or is configured by this element.                 |
| `mappings` → `ApiGatewayBasePathMapping` | reference, [*]; opposite `domainName`    | The mapping objects belonging to this domain or template structure. |

## `ApiGatewayBasePathMapping`

`ApiGatewayBasePathMapping` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway base path mapping. Its declaration gives the concept a precise home through domain name, api, stage. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute  | Type and multiplicity | What it captures and why it exists                                                | Accepted values and example                                                                                           |
| ---------- | --------------------- | --------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `basePath` | `String` [1]          | The URL prefix on the custom domain that is mapped to the selected API and stage. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `/orders/{orderId}`. |

### Relationships

| Relationship                          | Kind and multiplicity               | Meaning in the model                                            |
| ------------------------------------- | ----------------------------------- | --------------------------------------------------------------- |
| `domainName` → `ApiGatewayDomainName` | reference, [1]; opposite `mappings` | The custom domain on which this base-path mapping is installed. |
| `api` → `ApiGatewayApi`               | reference, [1]                      | The API that owns or is configured by this element.             |
| `stage` → `ApiGatewayStage`           | reference, [?]                      | The API Gateway deployment stage selected by this mapping.      |

## `ApiGatewayApiKey`

`ApiGatewayApiKey` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway api key. Its declaration gives the concept a precise home through stages. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute         | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                                   | Accepted values and example                                                                                           |
| ----------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------- |
| `apiKeyName`      | `String` [1]          | The api key name that identifies this api gateway api key in the model and its generated AWS configuration.                                                                                                                                                                          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`.           |
| `enabled`         | `Boolean` [1]         | Whether this capability or connection is active in the modeled scenario. Keeping the switch explicit distinguishes an intentional omission from a temporarily disabled design. Within `ApiGatewayApiKey`, it applies to this specific element and its role in the surrounding model. | Either `true` or `false`. Example: `true`.                                                                            |
| `value`           | `String` [1]          | The value carried by this parameter or expression. It is kept separate from the name so references can remain stable while deployment values change. Within `ApiGatewayApiKey`, it applies to this specific element and its role in the surrounding model.                           | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `READY`.             |
| `descriptionText` | `String` [1]          | The provider-facing descriptive text emitted for this resource. It gives an operator context in the generated template and console without carrying runtime behavior. Within `ApiGatewayApiKey`, it applies to this specific element and its role in the surrounding model.          | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Orders processing`. |

### Relationships

| Relationship              | Kind and multiplicity | Meaning in the model                                                                                                                                                                                   |
| ------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `stages` → `RestApiStage` | reference, [*]        | The `stages` reference on `ApiGatewayApiKey` keeps the provider deployment stages under the PSM root. A `RestApiStage` can remain independently owned and can participate in other parts of the model. |

## `ApiGatewayUsagePlan`

`ApiGatewayUsagePlan` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway usage plan. Its declaration gives the concept a precise home through api stages. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute            | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                             | Accepted values and example                                                                                                             |
| -------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------- |
| `usagePlanName`      | `String` [1]          | The usage plan name that identifies this api gateway usage plan in the model and its generated AWS configuration. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                         | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `ApiGatewayUsagePlanExample`.          |
| `descriptionText`    | `String` [1]          | The provider-facing descriptive text emitted for this resource. It gives an operator context in the generated template and console without carrying runtime behavior. Within `ApiGatewayUsagePlan`, it applies to this specific element and its role in the surrounding model. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Orders processing`.                   |
| `throttleBurstLimit` | `Integer` [1]         | The largest short burst of requests permitted by the usage plan before throttling. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                        | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                        |
| `throttleRateLimit`  | `Double` [1]          | The sustained request rate permitted by the usage plan. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                                   | A numeric `Double` value; use the unit or boundary documented for this attribute. Example: `1`.                                         |
| `quotaLimit`         | `Integer` [1]         | The number of requests a client may make during one quota period. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                         | A numeric `Integer` value; use the unit or boundary documented for this attribute. Example: `1`.                                        |
| `quotaPeriod`        | `String` [1]          | The calendar unit over which the usage-plan quota is counted. Generation role: The artifact generator references this feature in `mde/generation/awspsm-to-artifacts/lib/cfn.eol`.                                                                                             | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Api Gateway Usage Plan Quota Period`. |

### Relationships

| Relationship                 | Kind and multiplicity | Meaning in the model                             |
| ---------------------------- | --------------------- | ------------------------------------------------ |
| `apiStages` → `RestApiStage` | reference, [*]        | The REST API stages governed by this usage plan. |

## `ApiGatewayUsagePlanKey`

`ApiGatewayUsagePlanKey` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway usage plan key. Its declaration gives the concept a precise home through api key, usage plan. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute | Type and multiplicity | What it captures and why it exists                                                                    | Accepted values and example                                                                                 |
| --------- | --------------------- | ----------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `keyType` | `String` [1]          | The kind of client key attached to the usage plan, retained as text to match API Gateway terminology. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `orderId`. |

### Relationships

| Relationship                        | Kind and multiplicity | Meaning in the model                             |
| ----------------------------------- | --------------------- | ------------------------------------------------ |
| `apiKey` → `ApiGatewayApiKey`       | reference, [1]        | The API key associated with this usage-plan key. |
| `usagePlan` → `ApiGatewayUsagePlan` | reference, [1]        | The usage plan to which this key is attached.    |

## `ApiGatewayDeployment`

`ApiGatewayDeployment` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway deployment. Its declaration gives the concept a precise home through api. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute         | Type and multiplicity | What it captures and why it exists                                                                                                                                                                                                                                              | Accepted values and example                                                                                           |
| ----------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `descriptionText` | `String` [1]          | The provider-facing descriptive text emitted for this resource. It gives an operator context in the generated template and console without carrying runtime behavior. Within `ApiGatewayDeployment`, it applies to this specific element and its role in the surrounding model. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `Orders processing`. |

### Relationships

| Relationship      | Kind and multiplicity | Meaning in the model                                |
| ----------------- | --------------------- | --------------------------------------------------- |
| `api` → `RestApi` | reference, [1]        | The API that owns or is configured by this element. |

## `ApiGatewayTracingConfig`

`ApiGatewayTracingConfig` is an API Gateway deployment record in the AWS platform-specific model. It carries the provider settings for api gateway tracing config. Its declaration gives the concept a precise home through its declared properties. Its references provide the wiring that lets generation assemble the corresponding AWS design.

Direct supertypes: `TracingConfig`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

This class declares no attributes of its own. It inherits the attributes of its supertype, if any.

### Relationships

This class declares no direct relationships.

## `WafWebAclAssociation`

The explicit attachment of a WAF web ACL to an API resource that supports web-application firewall protection.

Direct supertypes: `AwsResource`. Inherited attributes and marker capabilities are documented in the [shared kernel](../shared-kernel.md); this section lists every attribute declared by this class.

### Declared attributes

| Attribute   | Type and multiplicity | What it captures and why it exists                                     | Accepted values and example                                                                                                                               |
| ----------- | --------------------- | ---------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `webAclArn` | `String` [1]          | The ARN of the WAF web ACL associated with the protected API resource. | A free-form `String`, subject to this class's semantic meaning and any EVL constraints. Example: `arn:aws:lambda:us-east-1:123456789012:function:orders`. |

### Relationships

| Relationship                                  | Kind and multiplicity | Meaning in the model                                                                                                                                                                                                           |
| --------------------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `protectedResource` → `WafAssociableResource` | reference, [1]        | The `protectedResource` reference on `WafWebAclAssociation` identifies the source represented by protected resource. A `WafAssociableResource` can remain independently owned and can participate in other parts of the model. |
