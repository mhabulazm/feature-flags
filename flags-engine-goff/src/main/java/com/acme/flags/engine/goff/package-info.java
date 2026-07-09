/**
 * Pre-ratification skeleton of the GO Feature Flag (GOFF) {@code FlagEngine} adapter, built on the
 * OpenFeature Java SDK.
 *
 * <p>GOFF is the recommended engine in ADR 0002, but that ADR is a draft and blocks implementation
 * start until it is ratified. This module compiles into the reactor so the adapter's shape and its
 * conformance gate are ready on ratification, but it does not evaluate flags yet:
 * {@code GoffFlagEngine}'s methods throw until the OpenFeature/GOFF dependencies are added and the
 * mapping is implemented. See {@code flags-engine-goff/README.md} for the completion steps.
 */
package com.acme.flags.engine.goff;
