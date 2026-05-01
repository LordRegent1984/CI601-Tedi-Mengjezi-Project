package slotengine.generators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slotengine.reels.Reels;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;

public class ReelGenerator {
    private final Reels r = new Reels();
    private static final Logger LOGGER = LoggerFactory.getLogger(ReelGenerator.class);

    public ReelGenerator() {
    }

    public int[] generateReelArray(byte[] seededReel) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(seededReel);

            // Retrieves the 3 full reel set of symbols
            int[][] reelSets = getReelSets();
            if (reelSets == null) {
                return null;
            }

            // Identifies the stop and retrieves the remaining symbols
            return getReelWindows(buffer, reelSets);
        } catch (Exception e) {
            LOGGER.error("Exception generateReels: ", e);
            return null;
        }
    }

    private int[][] getReelSets() {
        try {
            return new int[][]{
                    r.getReelSetRowOne(),
                    r.getReelSetRowTwo(),
                    r.getReelSetRowThree()
            };
        } catch (Exception e) {
            LOGGER.error("Exception getFullSets: ", e);
            return null;
        }
    }

    private int[] getReelWindows(ByteBuffer buffer, int[][] selectedSet) {
        try {
            // Initializes a 3x3 grid (3 reels, each with a 3-symbol window)
            int[][] reelWindows = new int[3][3];

            for (int i = 0; i < 3; i++) {
                // Combines 4 bytes to determine each stop position
                long stopValue = Integer.toUnsignedLong(buffer.getInt(i * 4));
                int stop = (int) (stopValue % selectedSet[i].length);

                // Populates the remaining symbols based off the stop
                reelWindows[i][0] = selectedSet[i][stop];
                reelWindows[i][1] = selectedSet[i][(stop + 1) % selectedSet[i].length];
                reelWindows[i][2] = selectedSet[i][(stop + 2) % selectedSet[i].length];
            }

            return IntStream.concat(IntStream.of(reelWindows[0]),
                    IntStream.concat(IntStream.of(reelWindows[1]),
                            IntStream.of(reelWindows[2]))).toArray();
        } catch (Exception e) {
            LOGGER.error("Exception getReelWindows: ", e);
            return null;
        }
    }
}