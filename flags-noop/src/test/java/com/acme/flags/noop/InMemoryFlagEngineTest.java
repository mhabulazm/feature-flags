package com.acme.flags.noop;

import com.acme.flags.contracttest.FlagEngineContractTest;
import com.acme.flags.spi.FlagEngine;

class InMemoryFlagEngineTest extends FlagEngineContractTest {

    @Override
    protected FlagEngine engine() {
        return new InMemoryFlagEngine(new FlagOverridesProperties());
    }
}
