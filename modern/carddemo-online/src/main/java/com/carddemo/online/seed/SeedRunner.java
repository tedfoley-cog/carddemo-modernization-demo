package com.carddemo.online.seed;

import com.carddemo.online.config.CardDemoProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Applies carddemo.seed.mode at start-up: none | if-empty | reload | users. */
@Component
public class SeedRunner implements ApplicationRunner {
    private final SeedLoader loader;
    private final CardDemoProperties props;

    public SeedRunner(SeedLoader loader, CardDemoProperties props) {
        this.loader = loader;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        String mode = props.seed().mode() == null ? "none" : props.seed().mode();
        if ("reload".equals(mode) || ("if-empty".equals(mode) && loader.isEmpty())) {
            loader.reload();
        } else if ("users".equals(mode)) {
            loader.reloadUsers();
        }
    }
}
