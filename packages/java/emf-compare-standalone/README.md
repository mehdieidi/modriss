# EMF Compare standalone adapter

This reactor module packages the official Eclipse EMF Compare core OSGi bundle as an ordinary
Maven dependency. It intentionally contains no Eclipse IDE, RCP, or UI components.

The build downloads the immutable `3.5.3.202605071334` bundle from the official Eclipse release
repository and verifies SHA-256
`F4B25742EAFDA5EFFA4904BC028BC64638612B41AE9233B62956AE0EBF4F357C` before unpacking it into this
module's output JAR. This avoids developer-local installation steps while retaining provenance and
reproducibility. The upstream bundle is licensed under the Eclipse Public License; its original
license and notice resources are retained in the packaged output.

Source and release repository:

- <https://github.com/eclipse-emf-compare/emf-compare>
- <https://download.eclipse.org/modeling/emf/compare/updates/releases/3.3/R202605071334/core/>
