package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slotengine.CreditCalculator;
import slotengine.generators.ReelGenerator;
import slotengine.generators.SeedGenerator;
import slotengine.wallet.Bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@WebServlet(name = "SpinServlet", urlPatterns = {"/spin"})
public class SpinServlet extends HttpServlet {
    private static final Properties PROPERTIES = new Properties();
    private static final Logger LOGGER = LoggerFactory.getLogger(SpinServlet.class);

    // Thread safe initialisation of PROPERTIES
    public void init() {
        synchronized (GameLauncherServlet.class) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("Properties.properties")) {
                if (input != null) {
                    PROPERTIES.load(input);
                }
            } catch (IOException ioe) {
                LOGGER.error("IOException init: ", ioe);
            }
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(PROPERTIES.getProperty("db_url_admin"),
                PROPERTIES.getProperty("db_username_admin"),
                PROPERTIES.getProperty("db_password_admin")
        );
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        double betAmount = Double.parseDouble(request.getParameter("betAmount"));
        double balance = Double.parseDouble(request.getParameter("balance"));
        int debitAmount = (int) Math.round(betAmount * 100);
        int currentBalance = (int) Math.round(balance * 100);

        if (currentBalance < debitAmount) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Insufficient Funds");
            return;
        }

        String accountID = request.getParameter("accountID");
        int gameID = Integer.parseInt(request.getParameter("gameID"));
        String serverSeedStr = request.getParameter("serverSeed");
        LOGGER.info("Server Seed: {}", serverSeedStr);
        String clientSeedStr = request.getParameter("clientSeed");
        LOGGER.info("Client Seed: {}", clientSeedStr);

        Bridge b = new Bridge();
        long wagerID = b.insertNewWager(accountID, clientSeedStr, gameID, debitAmount);
        if (wagerID == 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected Error");
            return;
        }

        SeedGenerator sg = new SeedGenerator();
        byte[] serverSeed = serverSeedStr.getBytes(StandardCharsets.UTF_8);
        byte[] clientSeed = clientSeedStr.getBytes(StandardCharsets.UTF_8);
        byte[] nonce = ByteBuffer.allocate(Long.BYTES).putLong(wagerID).array();
        byte[] seededReel = sg.generateSeededReel(serverSeed, clientSeed, nonce);
        if (seededReel == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected Error");
            return;
        }
        LOGGER.info("Seeded Reel: {}", Arrays.toString(seededReel));
        sg.storeReelValues(serverSeedStr, clientSeedStr, wagerID, seededReel);

        ReelGenerator rg = new ReelGenerator();
        int[] reelArray = rg.generateReelArray(seededReel);
        if (reelArray == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected Error");
            return;
        }
        LOGGER.info("Reel Array: {}", Arrays.toString(reelArray));

        CreditCalculator cc = new CreditCalculator();
        int creditAmount = cc.calculateWinnings(reelArray, debitAmount);
        b.updateCreditAmount(wagerID, creditAmount);

        // Calculate Win/Loss and new balance
        if (creditAmount > 0) {
            currentBalance = (currentBalance - debitAmount) + creditAmount;
        } else {
            currentBalance = currentBalance - debitAmount;
        }
        LOGGER.info("Credit Total: {}", creditAmount);

        // Formatted in decimals for UI
        double displayBalance = currentBalance / 100.0;
        double displayWin = creditAmount / 100.0;

        boolean isBalanceUpdated = updateBalance(accountID, currentBalance);
        if (!isBalanceUpdated) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected Error");
            return;
        }

        String betValuesStr = request.getParameter("betValues");
        List<Double> betArray = new ArrayList<>();
        for (String part : betValuesStr.split(",")) {
            betArray.add(Double.parseDouble(part.trim()));
        }

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html><html><head><title>Morning Reels</title>");
            out.println("<style>");

            // Centers the slot vertically and horizontally using Flexbox
            out.println("body { margin: 0; background: #fe8c00; color: white; font-family: sans-serif; " +
                    "display: flex; justify-content: center; align-items: center; min-height: 100vh; }");
            out.println(".game-wrapper { text-align: center; }");

            // Container for winning rounds
            out.println(".win-container {background: #1a1a1a; color: #fe8c00; padding: 20px; width: 500px; margin-bottom: 5px; " +
                    "box-sizing: border-box; font-weight: bold; font-size: 1.1em; text-transform: uppercase; border: 1px solid #333;}");

            // Container for accountID, session and balance
            out.println(".info-container { background: #1a1a1a; padding: 20px; width: 500px; " +
                    "margin-bottom: 5px; box-sizing: border-box; }");
            out.println(".info-container h2 { margin: 0 0 10px 0; text-transform: uppercase; letter-spacing: 1px; }");
            out.println(".info-container p { margin: 0; font-weight: bold; font-size: 1.1em; }");

            // 3x3 grid container
            out.println(".slot-machine { background: #1a1a1a; padding: 40px; width: 500px; " +
                    "display: inline-flex; flex-direction: column; align-items: center; box-sizing: border-box; }");

            // Container to visualise the reels
            out.println(".reels-container { display: flex; justify-content: center; gap: 10px; " +
                    "background: #000; padding: 25px; }");

            // WILD specific styling
            out.println(".wild { color: #fe8c00; font-size: 1.8em !important; }");

            // Reel and displayed symbol design
            out.println(".reel { background: #fff; color: #000; width: 110px; }");
            out.println(".symbol { height: 110px; line-height: 110px; font-size: 3em; " +
                    "font-weight: bold; border-bottom: 2px solid #000; }");
            out.println(".symbol:last-child { border-bottom: none; }");
            out.println(".controls { margin-top: 30px; width: 100%; }");
            out.println("select { padding: 12px; background: #2a2a2a; border: none; color: #fff; font-size: 1.1em; width: 50%; }");
            out.println("button { padding: 12px 35px; cursor: pointer; background: #fe8c00; border: none; " +
                    "color: #fff; font-weight: bold; font-size: 1.1em; text-transform: uppercase; margin-left: 5px; }");

            // Data display
            out.println("</style></head><body>");
            out.println("<div class='game-wrapper'>");

            // Displays to the user easier if they won
            if (creditAmount > 0) {
                out.println("<div class='win-container'>");
                out.println("Winner! - Credited: FUN " + String.format("%.2f", displayWin));
                out.println("</div>");
            }

            out.println("<div class='info-container'>");
            out.println("<h2>Morning Reels 3x3</h2>");

            // Displays the accountID and balance already predefined
            out.println("<p>Account: " + accountID + " | Session: " + clientSeedStr + " | Balance: " + displayBalance + " </p>");
            out.println("</div>");

            out.println("<div class='slot-machine'>");
            out.println("<div class='reels-container'>");

            // Creates the 3x3 grid using all 9 symbols from reelArray
            for (int c = 0; c < 3; c++) {
                out.println("<div class='reel'>");
                for (int r = 0; r < 3; r++) {
                    // Calculation to display correct index: Column 0 (0, 1, 2), Column 1 (3, 4, 5), Column 2 (6, 7, 8)
                    int symbolIndex = (c * 3) + r;
                    int symbolValue = reelArray[symbolIndex];

                    // Logic to display "WILD" instead of 10
                    if (symbolValue == 10) {
                        out.println("<div class='symbol wild'>WILD</div>");
                    } else {
                        out.println("<div class='symbol'>" + symbolValue + "</div>");
                    }
                }
                out.println("</div>");
            }
            out.println("</div>");

            // Form layer
            out.println("<div class='controls'>");

            // Spin button to call spin servlet
            out.println("<form action='spin' method='POST'>");

            // Generates dropdown displaying bet values
            out.println("<select name='betAmount'>");
            for (Double bet : betArray) {
                String selected = (bet == betAmount) ? "selected" : "";
                out.println("<option value='" + bet + "' " + selected + ">BET FUN " + String.format("%.2f", bet) + "</option>");
            }

            // Hidden input for the next servlet
            out.println("</select>");
            out.println("<button type='submit'>SPIN</button>");
            out.println("<input type='hidden' name='gameID' value='" + gameID + "'>");
            out.println("<input type='hidden' name='accountID' value='" + accountID + "'>");
            out.println("<input type='hidden' name='balance' value='" + displayBalance + "'>");
            out.println("<input type='hidden' name='serverSeed' value='" + serverSeedStr + "'>");
            out.println("<input type='hidden' name='clientSeed' value='" + clientSeedStr + "'>");
            out.println("<input type='hidden' name='betValues' value='" + betValuesStr + "'>");
            out.println("</form>");
            out.println("</div>");
            out.println("</div>");
            out.println("</div>");
            out.println("</body></html>");
        } catch (Exception e) {
            LOGGER.error("Exception doPost: ", e);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected Error");
        }
    }

    private boolean updateBalance(String accountID, int currentBalance) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("update test_accounts set balance = ? where account_id = ?")) {
            ps.setInt(1, currentBalance);
            ps.setString(2, accountID);
            int rs = ps.executeUpdate();
            return rs == 1;
        } catch (SQLException sqle) {
            LOGGER.error("SQLException getBalance: ", sqle);
            return false;
        }
    }
}