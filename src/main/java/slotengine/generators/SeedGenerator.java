package slotengine.generators;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.prng.EntropySource;
import org.bouncycastle.crypto.prng.drbg.HMacSP800DRBG;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

import static org.bouncycastle.util.Arrays.concatenate;

public class SeedGenerator {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Properties PROPERTIES = new Properties();
    private static final Logger LOGGER = LoggerFactory.getLogger(SeedGenerator.class);
    private final byte[] seededReel = new byte[32];
    private static final ThreadLocal<HMac> HMAC = ThreadLocal.withInitial(() ->
            new HMac(new SHA256Digest())
    );

    public SeedGenerator() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("Properties.properties")) {
            if (input != null) {
                PROPERTIES.load(input);
            }
        } catch (IOException ioe) {
            LOGGER.error("Error loading properties: ", ioe);
        }
    }

    public SeedGenerator(boolean isTest) {
    }

    // Required helper class to wrap seed as an entropy source
    private record StaticEntropySource(byte[] data) implements EntropySource {
        // Allows the values to be reproducable
        @Override
        public boolean isPredictionResistant() {
            return false;
        }

        // Returns the data
        @Override
        public byte[] getEntropy() {
            return data;
        }

        // Sets the data length in bits
        @Override
        public int entropySize() {
            return data.length * 8;
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(PROPERTIES.getProperty("db_url_admin"),
                PROPERTIES.getProperty("db_username_admin"),
                PROPERTIES.getProperty("db_password_admin")
        );
    }

    // Uses built in library to retrieve true random seed based off OS
    public byte[] generateServerSeed() {
        try {
            return SECURE_RANDOM.generateSeed(32);
        } catch (Exception e) {
            LOGGER.error("Exception generateServerSeed: ", e);
            return null;
        }
    }

    public byte[] generateSeededReel(byte[] serverSeed, byte[] clientSeed, byte[] nonce) {
        try {
            HMac hmac = HMAC.get();
            HMacSP800DRBG drbg = new HMacSP800DRBG(
                    hmac,
                    256, // The strength of the generator matched to the HMac
                    new StaticEntropySource(serverSeed),
                    clientSeed, // Personalisation to ensure unique reels
                    nonce
            );
            drbg.generate(seededReel,
                    null, //adds extra randomness - not needed
                    true); // reseeds to ensure extra security
            return seededReel;
        } catch (Exception e) {
            LOGGER.error("Exception generateSeededReel: ", e);
            return null;
        }
    }

    public boolean storeReelValues(String serverSeed, String clientSeed, long wagerID, byte[] generatedReel) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("insert into slot_record (server_seed, client_seed, " +
                     "nonce, generated_reel) values (?, ?, ?, ?)")) {
            ps.setString(1, serverSeed);
            ps.setString(2, clientSeed);
            ps.setLong(3, wagerID);
            ps.setBytes(4, generatedReel);
            int rs = ps.executeUpdate();
            return rs >= 1;
        } catch (SQLException sqle) {
            LOGGER.error("SQLException storeReelValues: ", sqle);
            return false;
        }
    }
}