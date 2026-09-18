/**
 * Composition root.
 *
 * <p>Bootstraps the Spring context, owns application-wide configuration, and is the only
 * module permitted to depend on every other module. Nothing depends on this module in return.
 *
 * <p><strong>Boundary contract.</strong> This module wires modules together; it must contain
 * no business logic. Any rule about how modules may interact is expressed here as an ArchUnit
 * test, so a violation fails the build rather than being caught in review.
 *
 * @see <a href="../../../../../../../../docs/adr/0001-modular-monolith.md">ADR-0001</a>
 */
package com.opspilot.platform.app;
