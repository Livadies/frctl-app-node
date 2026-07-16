# FSTEC and Russian software registry gap analysis

This repository is an engineering prototype and is not a certified information-security product.

## Separate processes

- Inclusion in the Russian software registry is governed by Government Resolution No. 1236 and is separate from security certification.
- Certification of an information-protection product is a formal process under FSTEC Order No. 55, involving an applicant, accredited certification body and testing laboratory.
- FSTEC Order No. 240 defines certification of secure software-development processes; Order No. 230 of 30 June 2025 amended that process.
- Development/production of means for protecting confidential information may trigger separate licensing requirements. Counsel and an accredited partner must classify the exact product and activity.

## Evidence the project still needs

- exact certified-product boundary and target assurance level;
- threat model linked to the FSTEC threat database and applicable system class;
- secure-development regulations, role separation and personnel controls;
- reproducible builds, dependency inventory, SBOM, vulnerability handling and update policy;
- source-code analysis, fuzzing, penetration testing and independent laboratory evidence;
- Russian-controlled build/signing infrastructure and documented key lifecycle;
- data localization, personal-data and telemetry retention decisions;
- installer, configuration guides, administrator guides and formal test methods.

The marketplace, remote-access node, AI assistant and resource-sharing network should be separate certification boundaries. Certifying one monolith would multiply the evidence and supply-chain surface.

## Primary references

- https://publication.pravo.gov.ru/documents/block/foiv041?index=4
- https://publication.pravo.gov.ru/documents/block/foiv041
- https://reestr.digital.gov.ru/documents/

