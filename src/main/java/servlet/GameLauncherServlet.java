package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slotengine.generators.ReelGenerator;
import slotengine.generators.SeedGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

@WebServlet(name = "GameLauncherServlet", urlPatterns = {"/game"})
public class GameLauncherServlet extends HttpServlet {
    private static final Properties PROPERTIES = new Properties();
    private static final Logger LOGGER = LoggerFactory.getLogger(GameLauncherServlet.class);

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
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String accountID = request.getParameter("accountID");
        int gameID = Integer.parseInt(request.getParameter("gameID"));

        String clientSeedStr = UUID.randomUUID().toString();
        LOGGER.info("Client Seed: {}", clientSeedStr);
        int launchNonce = 1;

        // Generates the initial Master Seed for when the player logs in
        SeedGenerator sg = new SeedGenerator();
        byte[] serverSeed = sg.generateServerSeed();
        if (serverSeed == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected Error");
            return;
        }
        LOGGER.info("Server Seed: {}", Arrays.toString(serverSeed));

        int balance = getBalance(accountID);
        if (balance < 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected Error");
            return;
        }
        double displayBalance = balance / 100.0;
        LOGGER.info("Current Balance: {}", displayBalance);

        String betValuesStr = getBetValues(gameID);
        if (betValuesStr == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected Error");
            return;
        }

        List<Double> betArray = new ArrayList<>();
        for (String part : betValuesStr.split(",")) {
            betArray.add(Double.parseDouble(part.trim()));
        }

        byte[] clientSeed = clientSeedStr.getBytes(StandardCharsets.UTF_8);
        byte[] nonce = ByteBuffer.allocate(Long.BYTES).putLong(launchNonce).array();
        byte[] seededReel = sg.generateSeededReel(serverSeed, clientSeed, nonce);
        LOGGER.info("Launch Seeded Reel: {}", Arrays.toString(seededReel));

        ReelGenerator rg = new ReelGenerator();
        int[] reelArray = rg.generateReelArray(seededReel);
        LOGGER.info("Launch Reel Array: {}", Arrays.toString(reelArray));

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html><html><head><title>Morning Reels</title>");
            out.println("<style>");

            // Centers the slot vertically and horizontally using Flexbox
            out.println("body { margin: 0; background: #fe8c00; color: white; font-family: sans-serif; " +
                    "display: flex; justify-content: center; align-items: center; min-height: 100vh; }");
            out.println(".game-wrapper { text-align: center; }");

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
                out.println("<option value='" + bet + "'>BET FUN " + String.format("%.2f", bet) + "</option>");
            }

            // Hidden input for the next servlet
            out.println("</select>");
            out.println("<button type='submit'>SPIN</button>");
            out.println("<input type='hidden' name='gameID' value='" + gameID + "'>");
            out.println("<input type='hidden' name='accountID' value='" + accountID + "'>");
            out.println("<input type='hidden' name='balance' value='" + displayBalance + "'>");
            out.println("<input type='hidden' name='serverSeed' value='" + Arrays.toString(serverSeed) + "'>");
            out.println("<input type='hidden' name='clientSeed' value='" + clientSeedStr + "'>");
            out.println("<input type='hidden' name='betValues' value='" + betValuesStr + "'>");
            out.println("</form>");
            out.println("</div>");
            out.println("</div>");
            out.println("</div>");
            out.println("</body></html>");
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected Error");
        }
    }

    private int getBalance(String accountID) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("select balance from test_accounts where account_id = ?")) {
            ps.setString(1, accountID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("balance");
                } else {
                    return -1;
                }
            }
        } catch (SQLException sqle) {
            LOGGER.error("SQLException getBalance: ", sqle);
            return -1;
        }
    }

    private String getBetValues(int gameID) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("select bet_values from games where game_id = ?")) {
            ps.setInt(1, gameID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("bet_values");
                } else {
                    return null;
                }
            }
        } catch (SQLException sqle) {
            LOGGER.error("SQLException getBetValues: ", sqle);
            return null;
        }
    }
}