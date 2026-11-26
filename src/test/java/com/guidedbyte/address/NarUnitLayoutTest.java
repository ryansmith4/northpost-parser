package com.guidedbyte.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.guidedbyte.address.AddressFixtures.Fixture;
import com.guidedbyte.address.service.AddressParserService;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Rewrites the NAR sample addresses that have a unit so the unit sits somewhere else, and requires the same fields
 * back.
 *
 * <p>Every address in {@code nar_sample_addresses.csv} that has a unit writes it at the start of the street line
 * ({@code APT 8 55 GRANDE ALLÉE E}), because that is how the NAR bulk test builds addresses. Real input also puts the
 * unit on a line of its own or between commas. Each {@link Layout} moves the unit to one of those places; the street,
 * municipality, province, postal code and the unit itself must come out exactly as recorded for the original. About
 * 1,100 real unit values and street names go through each layout, which hand-written cases cannot match for variety.
 *
 * <p>Three sample rows with a unit are left out. In two the recorded unit is empty although the address has one (a
 * known weakness, see the compound-unit section of the sample file), so the recorded value is not the right answer. In
 * the third the unit is the word {@code STH}, which is not one of the words the parser accepts as a unit value;
 * {@code AddressParserTest} records what happens to it.
 */
@DisplayName("NAR Sample With The Unit Moved")
class NarUnitLayoutTest {

    /** A sample address that has a unit: name line, {@code APT <unit> <street>}, region line. */
    private static final Pattern UNIT_ADDRESS = Pattern.compile("([^\n]+)\nAPT (\\S+) ([^\n]+)\n([^\n]+)");

    private static final int MAX_REPORTED = 40;

    /** Where the unit is written. Each layout builds the address text from the four parts of the original. */
    enum Layout {
        UNIT_LINE_AFTER_STREET {
            @Override
            String write(String name, String unit, String street, String region) {
                return name + "\n" + street + "\nAPT " + unit + "\n" + region;
            }
        },
        UNIT_LINE_BEFORE_STREET {
            @Override
            String write(String name, String unit, String street, String region) {
                return name + "\nAPT " + unit + "\n" + street + "\n" + region;
            }
        },
        UNIT_LINE_WITH_THE_WORD_UNIT {
            @Override
            String write(String name, String unit, String street, String region) {
                return name + "\n" + street + "\nUNIT " + unit + "\n" + region;
            }
        },
        COMMA_AT_THE_END_OF_EACH_LINE {
            @Override
            String write(String name, String unit, String street, String region) {
                return name + "\n" + street + ",\nAPT " + unit + ",\n" + region;
            }
        },
        SINGLE_LINE_WITH_COMMAS {
            @Override
            String write(String name, String unit, String street, String region) {
                return street + ", APT " + unit + ", " + region;
            }
        };

        abstract String write(String name, String unit, String street, String region);
    }

    private static List<Fixture> withUnit;

    @BeforeAll
    static void loadSample() throws Exception {
        withUnit = new ArrayList<>();
        for (Fixture f : AddressFixtures.load(NarSampleRegressionTest.RESOURCE)) {
            Matcher m = UNIT_ADDRESS.matcher(f.address());
            if (!f.unit().isEmpty() && m.matches() && !f.unit().equals("STH")) {
                withUnit.add(f);
            }
        }
    }

    @Test
    void sampleHasEnoughAddressesWithUnit() {
        assertThat(withUnit).hasSizeGreaterThan(1000);
        // The unit expected back is the one written in the address, not only the one recorded
        assertThat(withUnit).allSatisfy(f -> {
            Matcher m = UNIT_ADDRESS.matcher(f.address());
            assertThat(m.matches()).isTrue();
            assertThat(m.group(2)).isEqualTo(f.unit());
        });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Layout.class)
    void sameFieldsWhereverTheUnitIsWritten(Layout layout) {
        var parser = new AddressParserService();
        List<String> diffs = new ArrayList<>();

        for (Fixture expected : withUnit) {
            Matcher m = UNIT_ADDRESS.matcher(expected.address());
            assertThat(m.matches()).isTrue();
            String rewritten = layout.write(m.group(1), m.group(2), m.group(3), m.group(4));

            var result = parser.parseAddress(rewritten);
            if (!result.successful()) {
                diffs.add(rewritten.replace("\n", " / ") + "\n    parse failed: " + result.errors());
                continue;
            }
            var c = result.components();
            var sb = new StringBuilder();
            field(sb, "streetNumber", expected.streetNumber(), c.streetNumber());
            field(sb, "streetName", expected.streetName(), c.streetName());
            field(sb, "streetType", expected.streetType(), c.streetType());
            field(sb, "streetDirection", expected.streetDirection(), c.streetDirection());
            field(sb, "unit", expected.unit(), c.unitNumber());
            field(sb, "city", expected.city(), c.municipality());
            field(sb, "province", expected.province(), c.province());
            field(sb, "postalCode", expected.postalCode(), c.postalCode());
            if (!sb.isEmpty()) {
                diffs.add(rewritten.replace("\n", " / ") + sb);
            }
        }

        if (!diffs.isEmpty()) {
            var msg = new StringBuilder();
            msg.append(String.format(
                    "%d of %d addresses give different fields when written as %s.\n",
                    diffs.size(), withUnit.size(), layout));
            diffs.stream()
                    .limit(MAX_REPORTED)
                    .forEach(d -> msg.append("  ").append(d).append('\n'));
            if (diffs.size() > MAX_REPORTED) {
                msg.append("  ... and ").append(diffs.size() - MAX_REPORTED).append(" more\n");
            }
            fail(msg.toString());
        }
    }

    private static void field(StringBuilder sb, String name, String expected, String actual) {
        if (!expected.equals(actual)) {
            sb.append("\n    ")
                    .append(name)
                    .append(": expected '")
                    .append(expected)
                    .append("' but got '")
                    .append(actual)
                    .append('\'');
        }
    }
}
