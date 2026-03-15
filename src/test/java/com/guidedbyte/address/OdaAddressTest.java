package com.guidedbyte.address;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.guidedbyte.address.service.AddressParserService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parameterized tests using addresses sourced from Statistics Canada's Open Database of Addresses (ODA) for British
 * Columbia and Quebec.
 *
 * <p>The CSV file {@code oda_test_addresses.csv} contains realistic multi-line Canadian addresses (with {@code \n} for
 * line breaks) alongside expected parsed component values. The test feeds each address to {@link AddressParserService}
 * and validates extracted components.
 */
@DisplayName("ODA Address Parsing")
class OdaAddressTest {

    private AddressParserService parser;

    @BeforeEach
    void setUp() {
        parser = new AddressParserService();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("odaAddresses")
    void shouldParseOdaAddress(
            String description,
            String inputAddress,
            String expStreetNo,
            String expStreetName,
            String expStreetType,
            String expStreetDir,
            String expUnit,
            String expCity,
            String expProvince,
            String expPostalCode) {

        var result = parser.parseAddress(inputAddress);

        assertSoftly(softly -> {
            softly.assertThat(result.successful())
                    .as("parse should succeed for: %s", description)
                    .isTrue();

            if (!result.successful()) return;

            var c = result.components();

            softly.assertThat(c.streetNumber()).as("streetNumber").isEqualToIgnoringCase(expStreetNo);

            if (!expStreetName.isEmpty()) {
                softly.assertThat(c.streetName()).as("streetName").isEqualToIgnoringCase(expStreetName);
            }

            if (!expStreetType.isEmpty()) {
                softly.assertThat(c.streetType()).as("streetType").isEqualToIgnoringCase(expStreetType);
            } else {
                softly.assertThat(c.streetType())
                        .as("streetType (should be empty)")
                        .isEmpty();
            }

            if (!expStreetDir.isEmpty()) {
                softly.assertThat(c.streetDirection()).as("streetDirection").isEqualToIgnoringCase(expStreetDir);
            } else {
                softly.assertThat(c.streetDirection())
                        .as("streetDirection (should be empty)")
                        .isEmpty();
            }

            if (!expUnit.isEmpty()) {
                softly.assertThat(c.unitNumber()).as("unitNumber").isEqualToIgnoringCase(expUnit);
            }

            softly.assertThat(c.municipality()).as("municipality").isEqualToIgnoringCase(expCity);

            softly.assertThat(c.province()).as("province").isEqualToIgnoringCase(expProvince);

            if (!expPostalCode.isEmpty()) {
                softly.assertThat(c.postalCode().replaceAll("\\s", ""))
                        .as("postalCode")
                        .isEqualToIgnoringCase(expPostalCode.replaceAll("\\s", ""));
            }
        });
    }

    /**
     * Reads the ODA test CSV where the first column is the full address (with literal {@code \n} representing line
     * breaks) and remaining columns are expected parsed component values.
     *
     * <p>CSV format (pipe-delimited):
     * address|expStreetNo|expStreetName|expStreetType|expStreetDir|expUnit|expCity|expProvince|expPostalCode
     */
    static Stream<Arguments> odaAddresses() throws Exception {
        List<Arguments> args = new ArrayList<>();

        try (var reader = new BufferedReader(new InputStreamReader(
                OdaAddressTest.class.getResourceAsStream("/oda_test_addresses.csv"), StandardCharsets.UTF_8))) {

            String line;
            boolean headerSkipped = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }

                String[] fields = line.split("\\|", -1);
                if (fields.length < 9) continue;

                // First field is the full address with literal \n for line breaks
                String rawAddress = fields[0].trim();
                String inputAddress = rawAddress.replace("\\n", "\n");

                String expStreetNo = fields[1].trim();
                String expStreetName = fields[2].trim();
                String expStreetType = fields[3].trim();
                String expStreetDir = fields[4].trim();
                String expUnit = fields[5].trim();
                String expCity = fields[6].trim();
                String expProvince = fields[7].trim();
                String expPostalCode = fields.length > 8 ? fields[8].trim() : "";

                // Build a short description for the test name
                String description = rawAddress.replace("\\n", ", ");
                if (description.length() > 80) {
                    description = description.substring(0, 77) + "...";
                }

                args.add(Arguments.of(
                        description,
                        inputAddress,
                        expStreetNo,
                        expStreetName,
                        expStreetType,
                        expStreetDir,
                        expUnit,
                        expCity,
                        expProvince,
                        expPostalCode));
            }
        }

        return args.stream();
    }
}
