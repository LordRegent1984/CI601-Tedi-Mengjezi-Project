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
import java.util.Arrays;
import java.util.Properties;

@WebServlet(name = "BetDetailsServlet", urlPatterns = {"/betdetails"})
public class BetDetailsServlet extends HttpServlet {
    private static final Properties PROPERTIES = new Properties();
    private static final Logger LOGGER = LoggerFactory.getLogger(BetDetailsServlet.class);

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
        String wagerIDStr = request.getParameter("wagerID");
        if (wagerIDStr == null) return;
        long wagerID = Long.parseLong(wagerIDStr);

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html><html><head><title>Wager Details</title>");
            out.println("<style>");
            out.println("body { margin: 0; background: #fe8c00; color: white; font-family: sans-serif; " +
                    "display: flex; justify-content: center; align-items: center; min-height: 100vh; }");
            out.println(".game-wrapper { text-align: center; }");

            // Container for wager details
            out.println(".info-container { background: #1a1a1a; padding: 20px; width: 500px; " +
                    "margin-bottom: 5px; box-sizing: border-box; border-left: 5px solid #fe8c00; }");
            out.println(".info-container h2 { margin: 0 0 10px 0; text-transform: uppercase; font-size: 1em; color: #fe8c00; }");
            out.println(".info-container p { margin: 5px 0; font-weight: bold; font-size: 0.9em; }");

            // Container for 3x3 grid
            out.println(".slot-machine { background: #1a1a1a; padding: 40px; width: 500px; " +
                    "display: inline-flex; flex-direction: column; align-items: center; box-sizing: border-box; }");
            out.println(".reels-container { display: flex; justify-content: center; gap: 10px; " +
                    "background: #000; padding: 25px; }");
            out.println(".reel { background: #fff; color: #000; width: 110px; }");
            out.println(".symbol { height: 110px; line-height: 110px; font-size: 3em; " +
                    "font-weight: bold; border-bottom: 2px solid #000; }");
            out.println(".wild { color: #fe8c00; font-size: 1.8em !important; }");
            out.println(".symbol:last-child { border-bottom: none; }");

            out.println(".back-btn { display: inline-block; margin-top: 20px; padding: 12px 35px; " +
                    "background: #1a1a1a; color: #fe8c00; text-decoration: none; font-weight: bold; " +
                    "border: 2px solid #fe8c00; text-transform: uppercase; }");

            out.println("</style></head><body>");
            out.println("<div class='game-wrapper'>");

            // Join query to get the reelArray from slot_record and wager information from bet_record
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("select b.debit_amount, b.credit_amount, b.when_placed, " +
                         "s.server_seed, s.client_seed from bet_record b join slot_record s on b.wager_id = s.nonce where b.wager_id = ?")) {
                ps.setLong(1, wagerID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double debitAmount = rs.getInt("debit_amount") / 100.0;
                        double creditAmount = rs.getInt("credit_amount") / 100.0;
                        Timestamp whenPlaced = rs.getTimestamp("when_placed");
                        String serverSeedStr = rs.getString("server_seed");
                        LOGGER.info("Server Seed: {}", serverSeedStr);
                        String clientSeedStr = rs.getString("client_seed");
                        LOGGER.info("Client Seed: {}", clientSeedStr);

                        out.println("<div class='info-container'>");
                        out.println("<h2>Wager ID: #" + wagerID + "</h2>");
                        out.println("<p>When Placed: " + whenPlaced + "</p>");
                        out.println("<p>Stake: FUN " + String.format("%.2f", debitAmount) + " | Return: FUN " +
                                String.format("%.2f", creditAmount) + "</p>");
                        out.println("</div>");

                        out.println("<div class='slot-machine'>");
                        out.println("<div class='reels-container'>");

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

                        ReelGenerator rg = new ReelGenerator();
                        int[] reelArray = rg.generateReelArray(seededReel);
                        if (reelArray == null) {
                            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected Error");
                            return;
                        }
                        LOGGER.info("Reel Array: {}", Arrays.toString(reelArray));

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
                    }
                }
            } catch (SQLException sqle) {
                LOGGER.error("SQLException doGet: ", sqle);
            }
        } catch (Exception e) {
            LOGGER.error("Exception doGet: ", e);
        }
    }
}