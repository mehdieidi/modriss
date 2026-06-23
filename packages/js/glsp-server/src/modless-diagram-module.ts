import {
  BindingTarget,
  GModelDiagramModule,
  InstanceMultiBinding,
  OperationHandlerConstructor,
  ServerModule,
} from "@eclipse-glsp/server";
import { injectable } from "inversify";
import { ModlessCreateEdgeHandler } from "./handlers/modless-create-edge-handler.js";
import { ModlessCreateNodeHandler } from "./handlers/modless-create-node-handler.js";
import { ModlessCommandStack } from "./modless-command-stack.js";
import { ModlessDiagramConfiguration } from "./modless-diagram-configuration.js";
import { ModlessSourceModelStorage } from "./modless-source-model-storage.js";

@injectable()
export class ModlessServerModule extends ServerModule {}

@injectable()
export class ModlessDiagramModule extends GModelDiagramModule {
  constructor(
    private readonly storageBinding: BindingTarget<ModlessSourceModelStorage> = ModlessSourceModelStorage,
  ) {
    super();
  }

  get diagramType(): string {
    return "modless-diagram";
  }

  protected bindSourceModelStorage(): BindingTarget<ModlessSourceModelStorage> {
    return this.storageBinding;
  }

  protected bindDiagramConfiguration(): BindingTarget<ModlessDiagramConfiguration> {
    return ModlessDiagramConfiguration;
  }

  protected bindCommandStack(): BindingTarget<ModlessCommandStack> {
    return ModlessCommandStack;
  }

  protected configureOperationHandlers(binding: InstanceMultiBinding<OperationHandlerConstructor>): void {
    super.configureOperationHandlers(binding);
    binding.add(ModlessCreateNodeHandler);
    binding.add(ModlessCreateEdgeHandler);
  }
}
