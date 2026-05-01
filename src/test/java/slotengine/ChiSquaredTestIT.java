package slotengine;

import org.junit.jupiter.api.Test;
import slotengine.generators.ReelGenerator;
import slotengine.generators.SeedGenerator;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChiSquaredTestIT {

    @Test
    void testSymbolFrequencyFairness() {
        SeedGenerator sg = new SeedGenerator(true);
        ReelGenerator rg = new ReelGenerator();

        String clientSeedStr = UUID.randomUUID().toString();
        byte[] clientSeed = clientSeedStr.getBytes(StandardCharsets.UTF_8);
        int totalSpins = 100000000;
        int milestone = 10000000;
        byte[] serverSeed = sg.generateServerSeed();
        long startTime = System.currentTimeMillis();

        // Number of symbols ranges from 1 to 10 so need 11 as Array's start from 0
        int numberOfSymbols = 11;

        AtomicLong counter = new AtomicLong(0);
        AtomicLong[] symbolCount = new AtomicLong[numberOfSymbols];

        // Atomic thread safe Bucket that stores the count of each symbol
        for (int i = 0; i < numberOfSymbols; i++) symbolCount[i] = new AtomicLong(0);

        IntStream.range(0, totalSpins).parallel().forEach(i -> {
            byte[] nonce = ByteBuffer.allocate(Long.BYTES).putLong(i).array();
            byte[] seededReel = sg.generateSeededReel(serverSeed, clientSeed, nonce);
            int[] grid = rg.generateReelArray(seededReel);

            // For each symbol created by the grid
            for (int symbolID : grid) {
                // Updates the bucket with detected symbol
                symbolCount[symbolID].incrementAndGet();
            }

            long currentCount = counter.incrementAndGet();
            if (currentCount % milestone == 0) {
                System.out.println((currentCount / 1000000) + " million spins");
            }
        });

        long endTime = System.currentTimeMillis();

        // 3x3 grid so checking for symbol frequency across all visible symbols
        long totalObservations = (long) totalSpins * 9;

        double[] expectedProbabilities = {
                0.0,                // Ignored as no symbol has key 0
                30.0 / 128.0,       // Key 1 = LV3
                20.0 / 128.0,       // Key 2 = LV2
                18.0 / 128.0,       // Key 3 = LV1
                14.0 / 128.0,       // Key 4 = MV3
                12.0 / 128.0,       // Key 5 = MV2
                10.0 / 128.0,       // Key 6 = MV1
                8.0 / 128.0,        // Key 7 = HV3
                6.0 / 128.0,        // Key 8 = HV2
                4.0 / 128.0,        // Key 9 = HV1
                6.0 / 128.0         // Key 10 = WILD
        };

        double chiSquared = 0;
        System.out.println("\n--- Chi-Squared Test Results ---");

        // Loops through each symbol to calculate the individual chi-squared value before totaling
        for (int i = 1; i < numberOfSymbols; i++) {
            long observedValue = symbolCount[i].get();
            double expectedValue = expectedProbabilities[i] * totalObservations;

            double difference = observedValue - expectedValue;
            chiSquared += (difference * difference) / expectedValue;

            System.out.println("SymbolID: " + i + ", Observed: " + observedValue + ", Expected: " + expectedValue);
        }

        System.out.println("\n--- Statistical Summary ---");
        System.out.println("Time Taken: " + (endTime - startTime) / 1000 + " seconds");
        System.out.println("Calculated Chi-Squared value: " + chiSquared);

        // Degrees of Freedom is 10 - 1 as there are 10 symbols
        double criticalValue = 16.92;
        System.out.println("Critical Value: " + criticalValue);

        assertTrue(chiSquared < criticalValue,
                "Chi-Squared value " + chiSquared + " exceeds critical value! Statistical bias detected.");
    }
}