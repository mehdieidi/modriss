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
import { CommandStack, DefaultCommandStack, SourceModelStorage } from "@eclipse-glsp/server";
import { inject, injectable } from "inversify";
import { ModlessSourceModelStorage } from "./modless-source-model-storage.js";
let ModlessCommandStack = class ModlessCommandStack extends DefaultCommandStack {
  storage;
  async execute(command) {
    await super.execute(command);
    await this.storage.persistModel();
  }
};
__decorate(
  [inject(SourceModelStorage), __metadata("design:type", ModlessSourceModelStorage)],
  ModlessCommandStack.prototype,
  "storage",
  void 0,
);
ModlessCommandStack = __decorate([injectable()], ModlessCommandStack);
export { ModlessCommandStack };
export { CommandStack };
