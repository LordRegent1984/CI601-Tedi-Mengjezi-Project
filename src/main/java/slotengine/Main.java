package slotengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slotengine.generators.ReelGenerator;
import slotengine.generators.SeedGenerator;
import slotengine.wallet.Bridge;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String accountID = "TestUser1";
        String clientSeedStr = UUID.randomUUID().toString();
        int gameID = 1001;
        int debitAmount = 20;

        // Generates the initial Master Seed for when the player logs in
        SeedGenerator sg = new SeedGenerator();
        byte[] serverSeed = sg.generateServerSeed();
        LOGGER.info("Server Seed: {}", Arrays.toString(serverSeed));
        if (serverSeed == null) {
            return;
        }

        // Stores wager in bet_record table
        Bridge b = new Bridge();
        long wagerID = b.insertNewWager(accountID, clientSeedStr, gameID, debitAmount);
        LOGGER.info("Wager ID: {}", wagerID);
        if (wagerID == 0) {
            LOGGER.error("wagerID is 0");
            return;
        }

        // Generates the seeded reel using generated server seed, accountID as clientSeed and wagerID as Nonce
        byte[] clientSeed = clientSeedStr.getBytes(StandardCharsets.UTF_8);
        byte[] nonce = ByteBuffer.allocate(Long.BYTES).putLong(wagerID).array();
        byte[] seededReel = sg.generateSeededReel(serverSeed, clientSeed, nonce);
        LOGGER.info("Master Seed: {}", Arrays.toString(seededReel));
        if (seededReel == null) {
            LOGGER.error("seededReel is null");
            return;
        }

        // Stores result in Database
        if (!sg.storeReelValues(Arrays.toString(serverSeed), clientSeedStr, wagerID, seededReel)) {
            LOGGER.error("Failed to store seededReel");
            return;
        }

        // Generates the reel array
        ReelGenerator rg = new ReelGenerator();
        int[] reelArray = rg.generateReelArray(seededReel);
        if (reelArray == null) {
            LOGGER.error("Error generating reels");
            return;
        }

        LOGGER.info("Reel Array: {}", Arrays.toString(reelArray));
        LOGGER.info("{}, {}, {}", reelArray[0], reelArray[3], reelArray[6]);
        LOGGER.info("{}, {}, {}", reelArray[1], reelArray[4], reelArray[7]);
        LOGGER.info("{}, {}, {}", reelArray[2], reelArray[5], reelArray[8]);

        CreditCalculator cc = new CreditCalculator();
        int creditAmount = cc.calculateWinnings(reelArray, debitAmount);
        if (creditAmount == -1) {
            LOGGER.error("Error calculating creditAmount");
            return;
        }

        LOGGER.info("Credit Total: {}", creditAmount);
        boolean isRecordUpdated = b.updateCreditAmount(wagerID, creditAmount);
        if (!isRecordUpdated) {
            LOGGER.error("Error updating bet_record");
        }
    }
}