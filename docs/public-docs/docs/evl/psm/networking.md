# AWS PSM validation: Networking

Networking rules verify that VPC attachments have subnets and security groups, modeled IDs do not contradict typed references, private connectivity assumptions are supported, DNS is enabled, and public administrative ingress is reviewed.

Source profile: `mde/validation/psm/rules/networking.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Treat the guard as the applicability boundary. When it evaluates to false, EVL skips the rule. The diagnostic is the message emitted at runtime, and its final sentence usually gives the repository's recommended repair.

---

## `VpcAttachmentHasSubnetsAndSecurityGroups`

**Context:** `AWSPSM!VpcAttachmentConfig`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/networking.evl:8`

### Why this rule exists

The rule checks whether vpc attachment has subnets and security groups. The vpc attachment config element provides the relevant evidence through subnet ids, subnets, security group ids, security groups. At this level, VPC, subnet, security-group, DNS, endpoint, and ingress assumptions agree with the resources that will actually be deployed. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: VPC attachment is missing subnet IDs/references or security groups.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.subnetIds.notEmpty() or self.subnets.notEmpty()) and (self.securityGroupIds.notEmpty() or self.securityGroups.notEmpty())
```

This rule reads: `subnetIds`, `subnets`, `securityGroupIds`, `securityGroups`.

### Diagnostic and repair

> VPC attachment is missing subnet IDs/references or security groups. Fix: provide at least one subnet and one security group for the attached Lambda/function resource.

**How to fix it:**

provide at least one subnet and one security group for the attached Lambda/function resource.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ModeledVpcAttachmentShouldNotContradictRawIds`

**Context:** `AWSPSM!VpcAttachmentConfig`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/networking.evl:14`

### Why this rule exists

The rule checks whether modeled vpc attachment should not contradict raw ids. It examines vpc, subnets, security groups, vpc id, subnet ids. Within this part of the model, VPC, subnet, security-group, DNS, endpoint, and ingress assumptions agree with the resources that will actually be deployed. The gap is VPC attachment mixes modeled VPC/subnet/security-group references with raw IDs. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.vpc.isDefined() or self.subnets.notEmpty() or self.securityGroups.notEmpty()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (not self.vpcId.hasText()) and self.subnetIds.isEmpty() and self.securityGroupIds.isEmpty()
```

This rule reads: `vpc`, `subnets`, `securityGroups`, `vpcId`, `subnetIds`, `securityGroupIds`.

### Diagnostic and repair

> VPC attachment mixes modeled VPC/subnet/security-group references with raw IDs. Fix: use modeled references for generated infrastructure, or raw IDs for imported infrastructure with rationale.

**How to fix it:**

use modeled references for generated infrastructure, or raw IDs for imported infrastructure with rationale.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PrivateSubnetsRequireEndpointsWhenFlagged`

**Context:** `AWSPSM!VpcAttachmentConfig`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/networking.evl:21`

### Why this rule exists

The rule checks whether private subnets require endpoints when flagged. It examines private subnets require nat or endpoints, required endpoints, required for private access, service name. Within this part of the model, VPC, subnet, security-group, DNS, endpoint, and ingress assumptions agree with the resources that will actually be deployed. The gap is VPC attachment requires NAT/endpoints but has incomplete endpoint references. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.privateSubnetsRequireNatOrEndpoints = true
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.requiredEndpoints.forAll(endpointItem | endpointItem.requiredForPrivateAccess <> true or endpointItem.serviceName.hasText())
```

This rule reads: `privateSubnetsRequireNatOrEndpoints`, `requiredEndpoints`, `requiredForPrivateAccess`, `serviceName`.

### Diagnostic and repair

> VPC attachment requires NAT/endpoints but has incomplete endpoint references. Fix: add serviceName for every required VpcEndpointReference or document NAT availability.

**How to fix it:**

add serviceName for every required VpcEndpointReference or document NAT availability.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `VpcShouldEnableDns`

**Context:** `AWSPSM!Vpc`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/networking.evl:31`

### Why this rule exists

The rule checks whether vpc should enable dns. It examines enable dns hostnames, enable dns support, resource label. Within this part of the model, VPC, subnet, security-group, DNS, endpoint, and ingress assumptions agree with the resources that will actually be deployed. The gap is VPC does not enable both DNS hostnames and DNS support. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.enableDnsHostnames = true) and (self.enableDnsSupport = true)
```

This rule reads: `enableDnsHostnames`, `enableDnsSupport`, `resourceLabel`.

### Diagnostic and repair

> VPC does not enable both DNS hostnames and DNS support. Fix: set enableDnsHostnames and enableDnsSupport to true unless explicitly unsupported.

**How to fix it:**

set enableDnsHostnames and enableDnsSupport to true unless explicitly unsupported.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `PortRangeValid`

**Context:** `AWSPSM!SecurityGroupRule`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/psm/rules/networking.evl:40`

### Why this rule exists

The rule checks whether port range valid. The security group rule element provides the relevant evidence through from port, to port. At this level, VPC, subnet, security-group, DNS, endpoint, and ingress assumptions agree with the resources that will actually be deployed. Missing evidence would leave an unresolved choice in generated infrastructure. The rule prevents the following failure: Security group rule has invalid port range.

### When it applies

No guard is defined, so the check runs for every instance of this context in the validated model.

No guard expression is present. Every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : (self.fromPort.isUndefined() and self.toPort.isUndefined()) or (self.fromPort.isDefined() and self.toPort.isDefined() and self.fromPort >= 0 and self.toPort <= 65535 and self.fromPort <= self.toPort)
```

This rule reads: `fromPort`, `toPort`.

### Diagnostic and repair

> Security group rule has invalid port range. Fix: set fromPort/toPort within 0..65535 and ensure fromPort <= toPort, or omit both for protocols that do not use ports.

**How to fix it:**

set fromPort/toPort within 0..65535 and ensure fromPort <= toPort, or omit both for protocols that do not use ports.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PublicAdminIngressRequiresReview`

**Context:** `AWSPSM!SecurityGroupRule`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/psm/rules/networking.evl:47`

### Why this rule exists

The rule checks whether public admin ingress requires review. It examines cidr ip, from port, to port, rule description. Within this part of the model, VPC, subnet, security-group, DNS, endpoint, and ingress assumptions agree with the resources that will actually be deployed. The gap is Security group rule exposes SSH/RDP to the public internet. A later transformation, generator, or reviewer would otherwise have to infer the missing decision.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : (self.cidrIp = '0.0.0.0/0') and self.fromPort.isDefined() and ((self.fromPort <= 22 and self.toPort >= 22) or (self.fromPort <= 3389 and self.toPort >= 3389))
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.ruleDescription.hasText() and self.ruleDescription.toLowerCase().matches('.*review.*|.*approved.*|.*exception.*')
```

This rule reads: `cidrIp`, `fromPort`, `toPort`, `ruleDescription`.

### Diagnostic and repair

> Security group rule exposes SSH/RDP to the public internet. Fix: restrict cidrIp, use a bastion/VPN/SSM access pattern, or add an approved exception in ruleDescription.

**How to fix it:**

restrict cidrIp, use a bastion/VPN/SSM access pattern, or add an approved exception in ruleDescription.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---
