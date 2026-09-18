package com.opspilot.platform.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the OpsPilot platform.
 *
 * <p>Component scanning starts at {@code com.opspilot.platform}, so every module is discovered
 * without this class naming any of them. Adding a module to the build is therefore enough to
 * wire it in; nothing here needs to change.
 */
@SpringBootApplication(scanBasePackages = "com.opspilot.platform")
public class OpsPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsPilotApplication.class, args);
    }
}
