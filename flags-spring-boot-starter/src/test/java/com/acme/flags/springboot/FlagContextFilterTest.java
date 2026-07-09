package com.acme.flags.springboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.flags.api.FlagContext;
import com.acme.flags.api.FlagContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FlagContextFilterTest {

    @AfterEach
    void clear() {
        FlagContextHolder.clear();
    }

    @Test
    void doFilter_populatesHolderFromResolver_duringChain() throws Exception {
        FlagContext resolved = FlagContext.forTenant("tenant-1");
        CapturingFilterChain chain = new CapturingFilterChain();
        FlagContextFilter filter = new FlagContextFilter(() -> resolved);

        filter.doFilter(null, null, chain);

        assertThat(chain.capturedDuringChain).isEqualTo(resolved);
    }

    @Test
    void doFilter_clearsHolder_afterChainReturns() throws Exception {
        FlagContextFilter filter = new FlagContextFilter(() -> FlagContext.forTenant("tenant-1"));

        filter.doFilter(null, null, new CapturingFilterChain());

        assertThat(FlagContextHolder.get()).isNull();
    }

    @Test
    void doFilter_clearsHolder_evenWhenChainThrows() {
        FlagContextFilter filter = new FlagContextFilter(FlagContext::anonymous);

        assertThatThrownBy(() -> filter.doFilter(null, null, new ThrowingFilterChain()))
                .isInstanceOf(ServletException.class);
        assertThat(FlagContextHolder.get()).isNull();
    }

    @Test
    void doFilter_fallsBackToAnonymous_whenResolverThrows() throws Exception {
        CapturingFilterChain chain = new CapturingFilterChain();
        FlagContextFilter filter = new FlagContextFilter(() -> {
            throw new IllegalStateException("resolver bug");
        });

        filter.doFilter(null, null, chain);

        assertThat(chain.capturedDuringChain).isEqualTo(FlagContext.anonymous());
    }

    private static final class CapturingFilterChain implements FilterChain {
        FlagContext capturedDuringChain;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            capturedDuringChain = FlagContext.current();
        }
    }

    private static final class ThrowingFilterChain implements FilterChain {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws ServletException {
            throw new ServletException("downstream failure");
        }
    }
}
