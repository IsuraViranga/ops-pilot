/**
 * Knowledge base.
 *
 * <p>Articles, versions and tags. This is the corpus the AI assistant retrieves from.
 *
 * <p><strong>Boundary contract.</strong> Only the {@code api} sub-package is visible to other
 * modules. Everything else is internal, enforced by ArchUnit. Cross-module references are by
 * identifier only - never a JPA association and never a database foreign key.
 *
 * @see <a href="../../../../../../../../docs/adr/0001-modular-monolith.md">ADR-0001</a>
 */
package com.opspilot.platform.knowledge;
