package com.acme.flags.springboot;

import com.acme.flags.api.FlagContext;
import com.acme.flags.api.FlagContextHolder;
import com.acme.flags.api.FlagContextResolver;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlagContextFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(FlagContextFilter.class);

    private final FlagContextResolver resolver;

    public FlagContextFilter(FlagContextResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            FlagContextHolder.set(resolveOrFallback());
            chain.doFilter(request, response);
        } finally {
            FlagContextHolder.clear();
        }
    }

    private FlagContext resolveOrFallback() {
        try {
            return resolver.resolve();
        } catch (RuntimeException e) {
            log.warn("FlagContextResolver failed, falling back to anonymous context", e);
            return FlagContext.anonymous();
        }
    }
}
