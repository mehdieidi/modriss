/**
 * SPEM process specifications per modeling level.
 */
import { CIM_PROCESS_PHASES } from "./spem-cim.mjs";
import { PIM_PROCESS_PHASES } from "./spem-pim.mjs";
import { PSM_PROCESS_PHASES } from "./spem-psm.mjs";

/** @type {Record<string, import('./process-types.mjs').ProcessPhaseSpec[]>} */
export const PROCESS_PHASES = {
  cim: CIM_PROCESS_PHASES,
  pim: PIM_PROCESS_PHASES,
  psm: PSM_PROCESS_PHASES,
};
