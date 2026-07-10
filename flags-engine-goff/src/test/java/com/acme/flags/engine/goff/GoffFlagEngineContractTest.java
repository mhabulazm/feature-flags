package com.acme.flags.engine.goff;

import com.acme.flags.contracttest.FlagEngineContractTest;
import com.acme.flags.spi.FlagEngine;
import org.junit.jupiter.api.Disabled;

/**
 * Conformance gate for {@link GoffFlagEngine}, pre-wired against the shared {@code FlagEngineContractTest}.
 *
 * <p>Disabled while the adapter is a skeleton: {@code GoffFlagEngine} throws until it is implemented,
 * so the contract cannot pass yet. On ADR 0002 ratification, implement the engine and remove the
 * {@code @Disabled} annotation to arm the conformance suite.
 */
@Disabled("GOFF provider wiring pending ADR 0002 ratification — implement GoffFlagEngine, then remove this annotation")
class GoffFlagEngineContractTest extends FlagEngineContractTest {

    @Override
    protected FlagEngine engine() {
        return new GoffFlagEngine();
    }
}
