package com.alloca.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Chạy thủ công khi cần seed DB dev/test: ./mvnw test -Dtest=DevSeedRunnerTest")
public class DevSeedRunnerTest {

    @Test
    void runDevSeed() throws Exception {
        DevSeedRunner.runSeed();
    }
}
