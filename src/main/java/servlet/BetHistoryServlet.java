package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Properties;

@WebServlet(name = "BetHistoryServlet", urlPatterns = {"/bethistory"})
public class BetHistoryServlet extends HttpServlet {
    private static final Properties PROPERTIES = new Properties();
    private static final Logger LOGGER = LoggerFactory.getLogger(BetHistoryServlet.class);

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
        LOGGER.info("accountID: {}", accountID);

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html><html><head><title>Bet History</title>");
            out.println("<style>");

            // Centers the slot vertically and horizontally using Flexbox
            out.println("body { margin: 0; background: #fe8c00; color: white; font-family: sans-serif; " +
                    "display: flex; justify-content: center; align-items: center; min-height: 100vh; }");
            out.println(".game-wrapper { text-align: center; }");

            // Main container styling
            out.println(".info-container { background: #1a1a1a; padding: 20px; width: 600px; " +
                    "margin-bottom: 5px; box-sizing: border-box; border-bottom: 2px solid #fe8c00; }");
            out.println(".info-container h2 { margin: 0; text-transform: uppercase; letter-spacing: 1px; }");

            // Table styling
            out.println(".history-box { background: #1a1a1a; padding: 20px; width: 600px; " +
                    "max-height: 400px; overflow-y: auto; box-sizing: border-box; }");
            out.println("table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 0.9em; }");
            out.println("th { background: #333; color: #fe8c00; padding: 10px; text-align: left; text-transform: uppercase; }");
            out.println("td { padding: 10px; border-bottom: 1px solid #333; color: #ddd; }");

            // Styling for the links
            out.println(".id-link { color: #3366cc; text-decoration: underline; font-weight: bold; }");

            out.println("</style></head><body>");
            out.println("<div class='game-wrapper'>");
            out.println("<div class='info-container'>");
            out.println("<h2>Bet History: " + accountID + "</h2>");
            out.println("</div>");

            out.println("<div class='history-box'>");
            out.println("<table><tr><th>wagerID</th><th>Stake</th><th>Return</th><th>When Placed</th></tr>");

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("select wager_id, debit_amount, credit_amount, " +
                         "when_placed from bet_record where account_id = ? order by wager_id DESC")) {
                ps.setString(1, accountID);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long wagerID = rs.getLong("wager_id");
                        double stake = rs.getInt("debit_amount") / 100.0;
                        double winnings = rs.getInt("credit_amount") / 100.0;
                        Timestamp whenPlaced = rs.getTimestamp("when_placed");

                        out.println("<tr>");
                        // Links to the betdetails servlet
                        out.println("<td><a class='id-link' href='betdetails?wagerID=" + wagerID + "'>" + wagerID + "</a></td>");
                        out.println("<td>" + String.format("%.2f", stake) + "</td>");
                        out.println("<td>" + String.format("%.2f", winnings) + "</td>");
                        out.println("<td>" + whenPlaced.toString().substring(0, 16) + "</td>");
                        out.println("</tr>");
                    }
                }
            } catch (SQLException sqle) {
                LOGGER.error("SQLException doGet: ", sqle);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected Error");
                return;
            }
            out.println("</table>");
            out.println("</div>");
            out.println("</div></body></html>");
        } catch (Exception e) {
            LOGGER.error("Exception doGet: ", e);
        }
    }
}