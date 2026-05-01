package slotengine.wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;

public class Bridge {
    private static final Properties PROPERTIES = new Properties();
    private static final Logger LOGGER = LoggerFactory.getLogger(Bridge.class);

    public Bridge() {
        try (InputStream input = getClass().getClassLoader().
                getResourceAsStream("Properties.properties")) {
            if (input != null) {
                PROPERTIES.load(input);
            }
        } catch (IOException ioe) {
            LOGGER.error("IOException Bridge: ", ioe);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(PROPERTIES.getProperty("db_url_admin"),
                PROPERTIES.getProperty("db_username_admin"),
                PROPERTIES.getProperty("db_password_admin")
        );
    }

    public long insertNewWager(String accountID, String sessionID, int gameID, int debitAmount) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("insert into bet_record " +
                     "(account_id, session_id, game_id, debit_amount, when_placed) " +
                     "values (?, ?, ?, ?, now()) returning wager_id")) {
            ps.setString(1, accountID);
            ps.setString(2, sessionID);
            ps.setInt(3, gameID);
            ps.setInt(4, debitAmount);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                } else{
                    return 0;
                }
            }
        } catch (SQLException sqle) {
            LOGGER.error("SQLException insertNewWager: ", sqle);
            return 0;
        }
    }

    public boolean updateCreditAmount(long wagerID, int creditAmount) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("update bet_record set credit_amount = ?, when_settled = " +
                     "now(), status = 'completed' where wager_id = ?")) {
            ps.setInt(1, creditAmount);
            ps.setLong(2, wagerID);
            int rs = ps.executeUpdate();
            return rs == 1;
        } catch (SQLException sqle) {
            LOGGER.error("SQLException updateCreditAmount: ", sqle);
            return false;
        }
    }
}