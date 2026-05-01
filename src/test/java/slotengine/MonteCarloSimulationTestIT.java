package slotengine;

import org.junit.jupiter.api.Test;
import slotengine.generators.ReelGenerator;
import slotengine.generators.SeedGenerator;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MonteCarloSimulationTestIT {

    @Test
    void testMonteCarloSimulation() {
        SeedGenerator sg = new SeedGenerator(true);
        CreditCalculator cc = new CreditCalculator();
        ReelGenerator rg = new ReelGenerator();

        String clientSeedStr = UUID.randomUUID().toString();
        byte[] clientSeed = clientSeedStr.getBytes(StandardCharsets.UTF_8);
        int totalSpins = 100000000;
        int debitAmount = 100;
        byte[] serverSeed = sg.generateServerSeed();
        long startTime = System.currentTimeMillis();

        AtomicLong counter = new AtomicLong(0);
        int milestone = 10000000;

        // IntStream will utilise all cores to parallelism the spins as they are unique entities
        long totalWin = IntStream.range(0, totalSpins).parallel().mapToLong(i -> {
            byte[] nonce = ByteBuffer.allocate(Long.BYTES).putLong(i).array();
            byte[] seededReel = sg.generateSeededReel(serverSeed, clientSeed, nonce);
            int[] grid = rg.generateReelArray(seededReel);

            // Counter to track current spin count
            long currentCount = counter.incrementAndGet();
            if (currentCount % milestone == 0) {
                System.out.println((currentCount / 1000000) + " million spins");
            }

            return cc.calculateWinnings(grid, debitAmount);
        }).sum();

        long totalBet = (long) totalSpins * debitAmount;
        long endTime = System.currentTimeMillis();
        double actualRTP = (double) totalWin / totalBet;

        System.out.println("\n--- Monte Carlo Simulation Results ---");
        System.out.println("Time Taken: " + (endTime - startTime) / 1000 + " seconds");
        System.out.println("Total Stake: " + totalBet);
        System.out.println("Total Winnings: " + totalWin);
        System.out.println("Actual RTP: " + (actualRTP * 100) + "%");
        System.out.println("Theoretical RTP: 95.66%");

        assertEquals(0.96, actualRTP,
                0.005, "RTP did not match theoretical 95%");
    }
}