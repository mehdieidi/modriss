import { Command, CommandStack, DefaultCommandStack, SourceModelStorage } from "@eclipse-glsp/server";
import { inject, injectable } from "inversify";
import { ModlessSourceModelStorage } from "./modless-source-model-storage.js";

@injectable()
export class ModlessCommandStack extends DefaultCommandStack {
  @inject(SourceModelStorage)
  protected storage!: ModlessSourceModelStorage;

  override async execute(command: Command): Promise<void> {
    await super.execute(command);
    await this.storage.persistModel();
  }
}

export { CommandStack };
