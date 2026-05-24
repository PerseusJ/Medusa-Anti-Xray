/**
 * Public API for third-party integration with the Anti-Xray plugin.
 *
 * <h2>API Stability Guarantees</h2>
 * <ul>
 *   <li><b>Package Stability:</b> Classes and interfaces within this package ({@code com.antixray.api})
 *       are public API and are guaranteed to remain stable within a MAJOR version of the plugin.</li>
 *   <li><b>Events:</b> Custom events defined in this package are additive. New fields and methods
 *       may be added in minor/patch versions, but existing fields/methods will not be removed or renamed
 *       within the same MAJOR version.</li>
 *   <li><b>Deprecation Policy:</b> Deprecated methods or classes are marked with the {@link java.lang.Deprecated}
 *       annotation and the {@code @deprecated} Javadoc tag. They will remain fully functional for at least
 *       one MINOR version before they are removed in a subsequent release.</li>
 * </ul>
 *
 * <h2>Internal Packages</h2>
 * Any packages or classes outside of {@code com.antixray.api} (such as subpackages or implementation classes)
 * are considered internal and do not offer any stability guarantees. They may be modified, moved, or deleted
 * at any time without notice.
 */
package com.antixray.api;
