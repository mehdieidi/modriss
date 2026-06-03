import {hydrateContent, initStaticInteractions} from "./content.js";

hydrateContent();
initStaticInteractions();

import("./scroll-engine.js")
.then(({initLandingAnimations}) => initLandingAnimations())
.catch((error) => {
  document.documentElement.classList.add("no-anime");
  console.warn("Anime.js animation layer could not be initialized.", error);
});
