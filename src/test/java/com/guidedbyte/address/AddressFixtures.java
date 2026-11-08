package com.guidedbyte.address;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the pipe-delimited address fixture files in {@code src/test/resources}
 * ({@code oda_test_addresses.csv}, {@code nar_sample_addresses.csv}).
 *
 * <p>Format, one fixture per line:
 *
 * <pre>
 * address|expStreetNo|expStreetName|expStreetType|expStreetDir|expUnit|expCity|expProvince|expPostalCode
 * </pre>
 *
 * The address column holds the full input with a literal {@code \n} for each line break. Lines starting with {@code #}
 * are comments and blank lines are ignored; the first other line is the column header. Files are UTF-8.
 */
final class AddressFixtures {

    static final String HEADER =
            "address|expStreetNo|expStreetName|expStreetType|expStreetDir|expUnit|expCity|expProvince|expPostalCode";

    private static final int COLUMNS = 9;

    private AddressFixtures() {}

    /** One input address and the component values expected from it. */
    record Fixture(
            String address,
            String streetNumber,
            String streetName,
            String streetType,
            String streetDirection,
            String unit,
            String city,
            String province,
            String postalCode) {

        /** Short single-line form of the address for test names and failure messages. */
        String description() {
            String d = address.replace("\n", ", ");
            return d.length() > 80 ? d.substring(0, 77) + "..." : d;
        }

        /** The fixture as one line of the file. */
        String toCsvLine() {
            return String.join(
                    "|",
                    address.replace("\n", "\\n"),
                    streetNumber,
                    streetName,
                    streetType,
                    streetDirection,
                    unit,
                    city,
                    province,
                    postalCode);
        }

        /**
         * Parses one data line.
         *
         * @return the fixture, or {@code null} when the line has too few columns
         */
        static Fixture parse(String line) {
            String[] f = line.split("\\|", -1);
            if (f.length < COLUMNS) {
                return null;
            }
            return new Fixture(
                    f[0].trim().replace("\\n", "\n"),
                    f[1].trim(),
                    f[2].trim(),
                    f[3].trim(),
                    f[4].trim(),
                    f[5].trim(),
                    f[6].trim(),
                    f[7].trim(),
                    f[8].trim());
        }
    }

    /** Loads every fixture in a classpath resource such as {@code /oda_test_addresses.csv}. */
    static List<Fixture> load(String resource) throws IOException {
        InputStream in = AddressFixtures.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("Fixture resource not found: " + resource);
        }
        List<Fixture> fixtures = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            boolean headerSeen = false;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (!headerSeen) {
                    headerSeen = true;
                    continue;
                }
                Fixture fixture = Fixture.parse(line);
                if (fixture != null) {
                    fixtures.add(fixture);
                }
            }
        }
        return fixtures;
    }
}
