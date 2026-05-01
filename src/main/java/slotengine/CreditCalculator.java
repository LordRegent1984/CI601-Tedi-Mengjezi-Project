package slotengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreditCalculator {
    private static final int[] topLineWin = {0, 3, 6};
    private static final int[] middleLineWin = {1, 4, 7};
    private static final int[] bottomLineWin = {2, 5, 8};
    private static final int[] leftToRightDiagonal = {0, 4, 8};
    private static final int[] rightToLeftDiagonal = {2, 4, 6};
    private static final int[][] allLines = {topLineWin, middleLineWin, bottomLineWin, leftToRightDiagonal, rightToLeftDiagonal};
    private static final Logger LOGGER = LoggerFactory.getLogger(CreditCalculator.class);

    public int calculateWinnings(int[] reelArray, int debitAmount) {
        int totalWin = 0;
        try {
            for (int[] line : allLines) {
                int s1 = reelArray[line[0]];
                int s2 = reelArray[line[1]];
                int s3 = reelArray[line[2]];

                if (s1 == s2 && s2 == s3) {
                    totalWin = totalWin + payTable(s1, debitAmount);
                } else {
                    int sw = hasWild(s1, s2, s3);
                    if (sw > 0) {
                        totalWin = totalWin + payTable(sw, debitAmount);
                    }
                }
            }
            return totalWin;
        } catch (Exception e) {
            LOGGER.error("Exception calculateWinnings: ", e);
            return -1;
        }
    }

    // Multiplier for each symbol
    public int payTable(int symbol, int debitAmount) {
        LOGGER.info("Winning Symbol: {}", symbol);
        double multipliedAmount = switch (symbol) {
            case 10 -> debitAmount * 150;
            case 9 -> debitAmount * 50;
            case 8 -> debitAmount * 30;
            case 7 -> debitAmount * 25;
            case 6 -> debitAmount * 15;
            case 5 -> debitAmount * 10;
            case 4 -> debitAmount * 5;
            case 3 -> debitAmount * 2;
            case 2 -> debitAmount;
            case 1 -> debitAmount * 0.5;
            default -> 0;
        };
        return (int) multipliedAmount;
    }

    public int hasWild(int s1, int s2, int s3) {
        // If 1 symbol contains a wild and the other 2 symbols match then return one non-wild symbol to +
        // calculate the symbol multiplier
        if (s1 == s2 && s3 == 10) return s1;
        if (s1 == s3 && s2 == 10) return s1;
        if (s2 == s3 && s1 == 10) return s2;

        // If 2 symbols contain Wilds then return the non-wild symbol to calculate the symbol multiplier
        if (s1 == 10 && s2 == 10) return s3;
        if (s2 == 10 && s3 == 10) return s1;
        if (s1 == 10 && s3 == 10) return s2;
        return -1;
    }
}