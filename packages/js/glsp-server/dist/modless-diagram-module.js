var __decorate =
  (this && this.__decorate) ||
  function (decorators, target, key, desc) {
    var c = arguments.length,
      r =
        c < 3
          ? target
          : desc === null
            ? (desc = Object.getOwnPropertyDescriptor(target, key))
            : desc,
      d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function")
      r = Reflect.decorate(decorators, target, key, desc);
    else
      for (var i = decorators.length - 1; i >= 0; i--)
        if ((d = decorators[i]))
          r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
  };
var __metadata =
  (this && this.__metadata) ||
  function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function")
      return Reflect.metadata(k, v);
  };
import { GModelDiagramModule, ServerModule } from "@eclipse-glsp/server";
import { injectable } from "inversify";
import { ModlessCreateEdgeHandler } from "./handlers/modless-create-edge-handler.js";
import { ModlessCreateNodeHandler } from "./handlers/modless-create-node-handler.js";
import { ModlessCommandStack } from "./modless-command-stack.js";
import { ModlessDiagramConfiguration } from "./modless-diagram-configuration.js";
import { ModlessSourceModelStorage } from "./modless-source-model-storage.js";
let ModlessServerModule = class ModlessServerModule extends ServerModule {};
ModlessServerModule = __decorate([injectable()], ModlessServerModule);
export { ModlessServerModule };
let ModlessDiagramModule = class ModlessDiagramModule extends GModelDiagramModule {
  storageBinding;
  constructor(storageBinding = ModlessSourceModelStorage) {
    super();
    this.storageBinding = storageBinding;
  }
  get diagramType() {
    return "modless-diagram";
  }
  bindSourceModelStorage() {
    return this.storageBinding;
  }
  bindDiagramConfiguration() {
    return ModlessDiagramConfiguration;
  }
  bindCommandStack() {
    return ModlessCommandStack;
  }
  configureOperationHandlers(binding) {
    super.configureOperationHandlers(binding);
    binding.add(ModlessCreateNodeHandler);
    binding.add(ModlessCreateEdgeHandler);
  }
};
ModlessDiagramModule = __decorate(
  [injectable(), __metadata("design:paramtypes", [Object])],
  ModlessDiagramModule,
);
export { ModlessDiagramModule };
