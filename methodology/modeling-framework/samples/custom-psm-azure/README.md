# Minimal Azure PSM extension

This example tests a custom Ecore language, imported ETL modules, EGX/EGL generation and explicit
EVL validation. It deliberately emits small text manifests rather than deployable Azure resources.

1. Import `pim.xmi` in the PIM workspace and save it.
2. Open **Cloud Platforms → Add platform**.
3. Supply `azure.ecore`, `transformation.zip`, `generation.zip` and `validation.zip`.
4. Select `main.etl` and `main.egx` as the entry scripts.
5. Close settings. In PIM, choose Azure using the arrow beside Generate PSM, then generate.
6. Edit the result on the main canvas, validate it and generate artifacts using the normal toolbar.

Expected files include `azure-application.txt` and `functions/Hello.txt`. Use the arrow beside
the provider tab to switch between Azure and AWS. Each package keeps one model; regenerating
updates that model after confirmation. Visual notation is optional and has its own settings tab. The example PIM is
structurally minimal and does not attempt to satisfy all native PIM semantic constraints.

Run `python methodology/modeling-framework/samples/custom-psm-azure/package.py` from the repository
root to rebuild the three uploadable ZIPs. See the [custom platform guide](../../../../docs/public-docs/docs/guides/custom-cloud-platforms.md)
for the complete package contract and execution requirements.
